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

#include "dev/leds.h"
#include "dev/cc2420/cc2420.h"
#include "dev/cc2420/cc2420_const.h"
#include "dev/spi.h"
#include <stdio.h>


static uint16_t stats[256];

#if 0

static void
hexprint(uint8_t v)
{
  const char hexconv[] = "0123456789abcdef";
  putchar(hexconv[v >> 4]);
  putchar(hexconv[v & 0x0f]);
}

#define printhex(a)do{ \
		int8_t i;\
		uint8_t * p = (uint8_t *)(&a);\
		for(i = sizeof(a) -1; i > -1; i--){\
			hexprint(p[i]);\
		}\
\
}while(0)


static void
do_rssi(void)
{
	uint8_t val = cc2420_rssi() + 128; // Move to decent range
	hexprint(val);
	putchar('\n');
}



#else

static void
do_rssi(void)
{
	static uint16_t ctr;
	stats[cc2420_rssi()+128]++;
	if(ctr == 0xFFFF){
		int16_t i;
		for(i=0; i < 256; i++){
			if(stats[i]){
				printf("%d:%u ", i-128, stats[i] );
				stats[i] = 0;
			}
		}
		puts("");
		ctr = 0;
	} else {
		ctr++;
	}
}

#endif
/*---------------------------------------------------------------------------*/
PROCESS(scanner_process, "RSSI Stats");
AUTOSTART_PROCESSES(&scanner_process);
/*---------------------------------------------------------------------------*/
PROCESS_THREAD(scanner_process, ev, data)
{
  PROCESS_BEGIN();
  /* switch mac layer off, and turn radio on */
  NETSTACK_MAC.off(0);
  cc2420_on();
  cc2420_set_channel(26);
  while(1) {
    do_rssi();

    PROCESS_PAUSE();
  }

  PROCESS_END();
}
/*---------------------------------------------------------------------------*/
