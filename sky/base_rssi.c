/*
 * Copyright (c) 2007, Swedish Institute of Computer Science.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the Institute nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 * This file is part of the Contiki operating system.
 *
 */

/**
 * \file
 *         Scanning 2.4 GHz radio frequencies using CC2420 and prints
 *         the values
 * \author
 *         Joakim Eriksson, joakime@sics.se
 */

#include "contiki.h"
#include "net/rime/rime.h"
#include "net/netstack.h"
#include "lib/ringbuf.h"

#include "dev/leds.h"
#include "dev/lpm.h"
#include "dev/cc2420/cc2420.h"
#include "dev/cc2420/cc2420_const.h"
#include "dev/spi.h"
#include "dev/uart1.h"
#include "rtimer.h"
#include "etimer.h"
#include <stdio.h>

#define LEDON(x) while(0)
#define LEDOFF(x)


#define RSSI_BUFSIZE 128 /*TODO CHANGE*/
// RTIMER_ARCH_SECOND/1000/1000*128 =  ~ 4
#define Period 8
#define MINSLACK 2


static uint8_t buf[RSSI_BUFSIZE];
static struct ringbuf rbuf;
static struct rtimer rtimer;
static rtimer_clock_t nextt;
static uint8_t curv = 0;
static uint16_t ctr = 0;
static struct {
	uint8_t valid;
	uint8_t above;
	uint8_t below;

} backlog;


static void samp(struct rtimer *t, void *ptr);

#if 1
static uint8_t bitCount (uint8_t value) {
    unsigned int count = 0;
    while (value > 0) {           // until all bits are zero
        if ((value & 1) == 1)     // check lower bit
            count++;
        value >>= 1;              // shift bits, removing lower bit
    }
    return count;
}
#else
static uint8_t bitCount (uint8_t value) {
    uint8_t count = 0;
    uint8_t pos = 0;
    for(pos= 0; pos < 8; pos++){
    	if(1 << pos | value) count++;
    }
    return count;
}
#endif



static void flush(int changed){
	if(ctr > 0xFF){
		if(ringbuf_put(&rbuf, curv) == 0){
			printf("PANIC");
		}
		if(ringbuf_put(&rbuf, 0xFF) == 0){
			printf("PANIC");
		}
		ctr -= 0xFF;
	}

	if(changed){

		if(ringbuf_put(&rbuf, curv) == 0){
			printf("PANIC");
		}
		if(ringbuf_put(&rbuf, ctr) == 0){
			printf("PANIC");
		}

		ctr = 0;
	}
}

static void lost(int why){
	if(curv != why){
		if(RSSI_BUFSIZE - ringbuf_elements(&rbuf) <= 2){
			curv = 3;
			ctr++;
			return;
		}
		flush(1);
		curv = why;
	}  else {
		ctr ++;
	}
}

static void resched(void){
	nextt += Period;

	while(RTIMER_CLOCK_LT(nextt, RTIMER_NOW() + MINSLACK )){
		lost(2);
		nextt += Period;
	}

	rtimer_set(&rtimer, nextt, 1, samp, NULL);
}

static void samp(struct rtimer *t, void *ptr) {
	static int block = 0;

	int free = RSSI_BUFSIZE - ringbuf_elements(&rbuf);
	if(free <= 4 || block  ){
		lost(1);
		block = 1;
		if(free >= 8) block = 0;
		resched();
		return;
	}
	uint8_t v = cc2420_rssi() + 128;

	backlog.valid <<= 1;
	backlog.above <<= 1;
	backlog.below <<= 1;

	switch(v - curv){
		case 1:
			backlog.above |= 1;
			backlog.valid |= 1;
			break;
		case 0:
			backlog.valid |= 1;
			break;
		case -1:
			backlog.below |= 1;
			backlog.valid |= 1;
			break;
	}

	if(! (backlog.valid & 1)){
		ctr += bitCount(backlog.valid);
		flush(1);
		curv = v;
		backlog.valid = 1;
	} else if (backlog.valid == 0xFF) {
		ctr++;
		if(backlog.below == 0 && bitCount(backlog.above) > 4){
			flush(1);
			backlog.below = ~backlog.above;
			backlog.above = 0;
			curv += 1;
		} else if(backlog.above == 0 && bitCount(backlog.below) > 4){
			flush(1);
			backlog.above  = ~backlog.below;
			backlog.below = 0;
			curv -= 1;
		} else {
			flush(0);
		}
	}

	resched();
}




static void
hexprint(uint8_t v)
{
  uint8_t p;
  p = (v & 0xf0) >> 4;
  if(p < 10){ p += '0';} else { p += -10 + 'a';};
  putchar(p);
  p = (v & 0x0f);
  if(p < 10){ p += '0';} else { p += -10 + 'a';};
  putchar(p);
}

#define printhex(a) do{ \
	uint8_t * p = (uint8_t *)(&(a));\
	uint8_t size = sizeof(a) - 1;\
	{\
		int8_t i;\
		for(i = size; i >= 0 ; i--){\
		/*for(i = 0; i <  sizeof(a) ; i++){*/\
			hexprint(p[i]);\
		}\
	}\
\
}while(0)




/*---------------------------------------------------------------------------*/
PROCESS(scanner_process, "RSSI Scanner");
AUTOSTART_PROCESSES(&scanner_process);
/*---------------------------------------------------------------------------*/
PROCESS_THREAD(scanner_process, ev, data)
{
  static struct etimer bet;
  static rtimer_clock_t lastp;

  PROCESS_BEGIN();
  	  lpm_off();
   	  RINGBUF_INIT(&rbuf, buf);
     //ringbuf_init(&rbuf, buf, RSSI_BUFSIZE);
	  /* switch mac layer off, and turn radio on */
	  NETSTACK_MAC.off(0);
	  cc2420_on();
	  ///Let everything settle
	  etimer_set(&bet, 2);
	  PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&bet));
	  nextt = RTIMER_NOW() + Period;
	  if(rtimer_set(&rtimer, nextt, 1, samp, NULL) != RTIMER_OK) {
	      printf("Error setting\n");
	    }

	  while(1) {
			//Wait until ther is some data and also
			do{
				PROCESS_PAUSE();
				if(RTIMER_NOW() - lastp >  RTIMER_ARCH_SECOND ){
					printf("PANIC!!! No DATA\n");
				}
			} while(ringbuf_elements(&rbuf) < 2);
			LEDON(LEDS_GREEN);


			lastp = RTIMER_NOW();

			putchar('R');
			int octr = 0;
			while(ringbuf_elements(&rbuf) > 1 && octr < 30){

#if 0
				int t;
				t = ringbuf_get(&rbuf); if(t < 0) printf("PANIC!!!!");else hexprint(t);
				t = ringbuf_get(&rbuf); if(t < 0) printf("PANIC!!!!"); else hexprint(t);
#else
				hexprint(ringbuf_get(&rbuf));
				hexprint(ringbuf_get(&rbuf));
#endif
				octr++;
			}
			putchar('\n');
			LEDOFF(LEDS_GREEN);
	  }


  PROCESS_END();
}
/*---------------------------------------------------------------------------*/
