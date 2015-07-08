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

#include "etimer.h"
#include <stdio.h>
#include "platform-conf.h"

#define LEDON(x) while(0)
#define LEDOFF(x)


/* Imported stuff
 */

// CC2420

static uint16_t
getreg(enum cc2420_register regname)
{
  uint16_t value;

  CC2420_SPI_ENABLE();
  SPI_WRITE(regname | 0x40);
  value = (uint8_t)SPI_RXBUF;
  SPI_TXBUF = 0;
  SPI_WAITFOREORx();
  value = SPI_RXBUF << 8;
  SPI_TXBUF = 0;
  SPI_WAITFOREORx();
  value |= SPI_RXBUF;
  CC2420_SPI_DISABLE();

  return value;
}


//RTIME
static rtimer_clock_t
now(void)
{
  rtimer_clock_t t1, t2;
  do {
    t1 = TAR;
    t2 = TAR;
  } while(t1 != t2);
  return t1;
}
/* ***********************************/



#define RSSI_BUFSIZE 128 /*TODO CHANGE*/
//#define RTIMER_ARCH_SECOND (4096U*8)
// RTIMER_ARCH_SECOND/1000/1000*128 =  ~ 4
#define Period 4
#define MINSLACK 2

#define SHORTRANGE 10
#define BITRANGE 10
#define TWOBRANGE 10
#define TWOBRANGEOFF (('z' - '!')/2)

#if TWOBRANGE > SHORTRANGE
#error FIXME
#endif

#if 2 * SHORTRANGE + 2 * BITRANGE + TWOBRANGE + 'A' > 'z'
#error Out of range
#endif



static uint16_t buf[RSSI_BUFSIZE];
static uint8_t lastp = 0;



static uint8_t print(uint16_t prn, char * buf) {


	int16_t v = prn & 0xFF;
	int16_t c = prn >> 8;

	int8_t diff = v - lastp;
	uint8_t rv = 0;



	char off = 0;
	char dat = 0;
	if(diff != 0){
		// +-1 first
		off = 'A';

		//Negativ diff
		if(c == 1 && diff < 0 && diff > -(BITRANGE + 1)){
			off += -diff - 1;
			rv = 1;
		}

		//Postitive diff
		else if(c == 1 && diff > 0 && diff <  (BITRANGE + 1)){
			off += BITRANGE;
			off += diff - 1;
			rv = 1;
		}

		//One-Offset neg
		else if(diff == -1 && c != 1 && c < SHORTRANGE + 2){
			off += 2 * BITRANGE;
			off += c - 2;
			rv = 1;
		}

		//One-Offset pst
		else if(diff == +1 && c != 1 && c < SHORTRANGE + 2){
			off += 2 * BITRANGE + SHORTRANGE;
			off += c - 2;
			rv = 1;
		}

		//Some more range - Two-Byte
		else if(c < TWOBRANGE + 1 && diff >= -TWOBRANGEOFF && diff <= TWOBRANGEOFF) {
			off += 2 * BITRANGE + 2 * SHORTRANGE;
			off += c - 1;
			dat = '!' + diff + TWOBRANGEOFF;
			rv = 2;
		}

	}
	lastp = v;

	if(rv == 1){
		buf[3] = off;
		return 3;
	}

	if(rv == 2) {
		buf[2] = off;
		buf[3] = dat;
		return 2;
	}

#if 0
	uint8_t i;
	for(i = 0; i < 4; i++){
		buf[i] = (prn << i * 4) >> 3 * 4;
	}
#else
	int16_t t = c;
	buf[0] = (t  >> 4) + '0';
	buf[1] = (t & 0xF) + '0';
	buf[2] = (v  >> 4) + '0';
	buf[3] = (v & 0xF) + '0';


#endif

	return 0;
}

static void
watchdog_periodic(void)
{
	WDTCTL = (WDTCTL & 0xff) | WDTPW | WDTCNTCL | WDTTMSEL;
}


/*---------------------------------------------------------------------------*/
PROCESS(scanner_process, "RSSI Scanner");
AUTOSTART_PROCESSES(&scanner_process);
/*---------------------------------------------------------------------------*/
PROCESS_THREAD(scanner_process, ev, data) {
	static struct etimer bet;


	PROCESS_BEGIN()
	;
	lpm_off();
	//ringbuf_init(&rbuf, buf, RSSI_BUFSIZE);
	/* switch mac layer off, and turn radio on */
	NETSTACK_MAC.off(0);
	cc2420_on();
	//Let everything settle
	etimer_set(&bet, 2);
	PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&bet));

	//Make sure everythings is settled, before we take over the system.
	cc2420_rssi();

	//Ok run now




	//No more interruptions. We'll doe it all ourselves.
	dint();

	//We'll never leave this loop again. - Local variables are ok - and make things even faster

	uint8_t last = 0xFF;
	uint8_t ctr = 0;
	int16_t samples = 0;
	rtimer_clock_t nextt;
	uint8_t changed = 0; //Helper - should be removed by the compiler
	char txbuf[4];
	int8_t txstate = -1;
	uint8_t v;


	uint8_t read = 0;
	uint8_t write = 0;
	uint8_t symbc = 0;
	nextt = now();

	while (1) {
		//Next period.
		nextt += Period;
		samples ++;
		if(RTIMER_CLOCK_LT(nextt, now() + 1)) {
			v = 0;
		} else {
			while (RTIMER_CLOCK_LT(now(), nextt)){ // Wait for next. - slack time
				if(txstate > -1){
					if(IFG2 & UTXIFG1){
						TXBUF1 = txbuf[txstate];
						txstate ++;
						if(txstate == 4) txstate = -1;
						watchdog_periodic();
					}
				} else if(symbc == 40){
					txbuf[0] = ((samples & 0x1F00)  >> 8) + '0';
					txbuf[1] = ((samples & 0xF0)  >> 4) + '0';
					txbuf[2] = (samples & 0xF) + '0';
					txbuf[3] = '\n';
					txstate = 0;
					symbc = 0;
				} else	if(read != write) {
					txstate = print(buf[read], txbuf);
					read = (read + 1) % RSSI_BUFSIZE;
					symbc++;
				}

			}

			v = (uint8_t)((int16_t)((int8_t) getreg(CC2420_RSSI)) + 128);
		}


		changed = 1;
		if(v == last){
			ctr++;
			changed = 0;
		}
		// ctr == 0 is overflow
		if(ctr == 0 || changed){
			uint8_t t = (write + 1) % RSSI_BUFSIZE;
			if(t == read){
				//Buffer full - Set to 1. Will retry next time
				v = 1;
				//Count if buffer was full last time - this did not happen before
				if(last == v) ctr++;
			} else {
				write = t;
				if(changed){
					buf[write] = (ctr << 8) + last;
				} else {
					buf[write] = (0xFF << 8) + last;
				}

				ctr = 1;
			}
			last = v;


		}


	}

PROCESS_END();
}
/*---------------------------------------------------------------------------*/
