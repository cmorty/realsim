/*
 * Copyright (c) 2006, Swedish Institute of Computer Science.
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
 *         A program for burning a node ID into the flash ROM of a Tmote Sky node.
 * \author
 *         Adam Dunkels <adam@sics.se>
 */

#include "dev/leds.h"
#include "dev/watchdog.h"
#include "sys/node-id.h"
#include "contiki.h"
#include "sys/etimer.h"
#include "dev/serial-line.h"


#include <stdio.h>
#include <stdlib.h>

static struct etimer etimer;
static int newid;

PROCESS(burn_process, "Burn node id");
AUTOSTART_PROCESSES(&burn_process);
/*---------------------------------------------------------------------------*/
PROCESS_THREAD(burn_process, ev, data) {
	PROCESS_BEGIN();

		while (1) {
			etimer_set(&etimer, 2 * CLOCK_SECOND);
			PROCESS_WAIT_UNTIL(etimer_expired(&etimer));
			puts("01: Enter new ID:");
			PROCESS_WAIT_EVENT()
			;
			if (ev == serial_line_event_message && data != NULL ) {
				newid = atoi(data);
				if (newid == 0) {
					puts("Failed to parse id");
					continue;
				}
			} else {
				continue;
			}
			etimer_set(&etimer, 2 * CLOCK_SECOND);
			PROCESS_WAIT_EVENT();
			if (!etimer_expired(&etimer)) {
				puts("Unexpected event");
				continue;
			}
			puts("02: Reenter new ID");
			etimer_set(&etimer, 5 * CLOCK_SECOND);
			PROCESS_WAIT_EVENT()
			;
			if (ev == serial_line_event_message && data != NULL ) {
				if (newid != atoi(data)) {
					puts("IDs did not match");
					continue;
				}

				watchdog_stop();
				printf("Burning node id %d\n", newid);
				node_id_burn(newid);
				node_id_restore();
				printf("Restored node id %d\n", node_id);
				watchdog_start();
				if(newid == node_id){
					puts("03: Successfull");
				}
				newid = 0 ;

			} else {
				puts("Timed out.");
			}

		}

	PROCESS_END();
}
/*---------------------------------------------------------------------------*/

