#include "contiki.h"
#include "lib/list.h"
#include "lib/memb.h"
#include "lib/random.h"
#include "net/rime.h"
#include <stdio.h>

#define MAX_NEIGHBORS 16
#define MAX_PACKETS 10
#define MAX_NODES 16

/* Struct for neighbor info */
struct neighbor {
	struct neighbor *next;
	rimeaddr_t addr;
	uint16_t recv_count; 
	uint8_t rssi;
	uint8_t lqi;
};

/* Struct for broadcast messages*/
struct broadcast_message {
	uint16_t seqno;
	rimeaddr_t own_addr;
	struct neighbor n;
};

/* Struct for node adresses */
struct node_address {
	struct node_address *next;
	rimeaddr_t node_addr;
	uint16_t ttl;
};

/* Structs for connections */
static struct broadcast_conn broadcast;
static struct mesh_conn mesh;
static struct collect_conn tc;

/* Memory pool for list entries */
MEMB(neighbors_memb, struct neighbor, MAX_NEIGHBORS);

/* Neighbors_list */
LIST(neighbors_list);

/* Memory pool for addresses*/
MEMB(address_memb, struct node_address, MAX_NODES);

/* List for node adresses */
LIST(node_addresses);

/*---------------------------------------------------------------------------*/
PROCESS(scanning_process, "Scanning process");
AUTOSTART_PROCESSES(&scanning_process);
/*---------------------------------------------------------------------------*/

static void
broadcast_recv(struct broadcast_conn *c, const rimeaddr_t *from)
{
	struct neighbor *n;
	struct broadcast_message *m;

	m = packetbuf_dataptr();

	/* check if we already know this neighbor. */
	for(n = list_head(neighbors_list); n != NULL; n = list_item_next(n)) {
		if(rimeaddr_cmp(&n->addr, from)) {
			break;
		}
	}

	/* If n is NULL, this neighbor was not found in our list */
	if(n == NULL) {
		n = memb_alloc(&neighbors_memb);
		if(n == NULL) {
			return;
		}
		
		/* Initialize the fields. */
		rimeaddr_copy(&n->addr, from);
		n->recv_count = 0;
		n->rssi = 90;
		n->lqi = 100;

		/* Place the neighbor on the neighbor list. */
		list_add(neighbors_list, n);
	}
	
	/* Set new values */
	n->rssi = (packetbuf_attr(PACKETBUF_ATTR_RSSI) + 55 + n->rssi)/2;
	n->lqi = (packetbuf_attr(PACKETBUF_ATTR_LINK_QUALITY) + n->lqi)/2;
	n->recv_count++;
	
	 // Avoid align faults
	uint16_t tmp_seqno;
	memcpy(&(tmp_seqno), &(m->seqno), sizeof(m->seqno));

	/* Print out a message. 
	printf("broadcast message received from %d.%d with seqno %d, RSSI %u, LQI %u, ratio:(%d / %d) \n",
         from->u8[0], from->u8[1],
         tmp_seqno,
         packetbuf_attr(PACKETBUF_ATTR_RSSI) + 55,
         packetbuf_attr(PACKETBUF_ATTR_LINK_QUALITY),
         n->recv_count,
         tmp_seqno);*/
}

static void
sent(struct mesh_conn *c)
{
	printf("packet sent %d.%d \n", c->queued_data_dest.u8[0],c->queued_data_dest.u8[1]);
}
static void
timedout(struct mesh_conn *c)
{
	printf("packet timed out\n");
}
static void
recv(struct mesh_conn *c, const rimeaddr_t *from, uint8_t hops)
{
	struct broadcast_message *msg;
	
	msg = packetbuf_dataptr();
	
	/* Avoid Align faults */
	rimeaddr_t tmp_addr; memcpy(&(tmp_addr), &(msg->n.addr), sizeof(msg->n.addr));
	rimeaddr_t tmp_own_addr; memcpy(&(tmp_own_addr), &(msg->own_addr), sizeof(msg->own_addr));
	uint16_t tmp_seqno; memcpy(&(tmp_seqno), &(msg->seqno), sizeof(msg->seqno));
	uint16_t tmp_recv_count; memcpy(&(tmp_recv_count), &(msg->n.recv_count), sizeof(msg->n.recv_count));
	uint8_t tmp_rssi; memcpy(&(tmp_rssi), &(msg->n.rssi), sizeof(msg->n.recv_count));
	uint8_t tmp_lqi; memcpy(&(tmp_lqi), &(msg->n.lqi), sizeof(msg->n.lqi));

	/* Print received Information */
	/* Packets from Node 1 -> Node 2 have a success ratio of x to be received by Node 2*/
	printf("edge::%d.%d::%d.%d::%d::%d::%d::\n",
		tmp_addr.u8[0],
		tmp_addr.u8[1],
		tmp_own_addr.u8[0],
		tmp_own_addr.u8[1],
		(tmp_recv_count * 100) / tmp_seqno,
		tmp_rssi,
		tmp_lqi);
}

static void
collect_recv(const rimeaddr_t *from, uint8_t seqno, uint8_t hops)
{
	struct node_address *node;
	
	/* Check if node in list */
	for(node = list_head(node_addresses); node != NULL; node = list_item_next(node)) {
		if(rimeaddr_cmp(&node->node_addr, from)) {
			break;
		}
	}

	/* Add node to list and set ttl */
	if(node == NULL) {	  
		node = memb_alloc(&address_memb);
		if(node == NULL) {
		  return;
		}
		rimeaddr_copy(&node->node_addr,from);
		node->ttl = 30;
		list_add(node_addresses, node);
	} else {
		node->ttl = 30;
	}
	
	/* Print nodes and/or remove dead nodes */
	for(node = list_head(node_addresses); node != NULL; node = list_item_next(node)) {
		if(node->ttl == 0){
			list_remove(node_addresses, node);
		}
		printf("node::%d.%d::", node->node_addr.u8[0], node->node_addr.u8[1]);
	}
	printf("\n");
}

static const struct broadcast_callbacks broadcast_call = {broadcast_recv};
static const struct mesh_callbacks callbacks = {recv, sent, timedout};
static const struct collect_callbacks collect_callbacks = {collect_recv};
/*---------------------------------------------------------------------------*/

PROCESS_THREAD(scanning_process, ev, data)
{
	static struct etimer et;
	static rimeaddr_t oldparent;
	static uint16_t seqno = 1;
	struct broadcast_message msg;
	const rimeaddr_t *parent;
	struct node_address *node;

	PROCESS_EXITHANDLER(broadcast_close(&broadcast); mesh_close(&mesh); collect_close(&tc);)

	PROCESS_BEGIN();
	
	/* Init */
	etimer_set(&et, CLOCK_SECOND * 2);
	PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));
	
	broadcast_open(&broadcast, 129, &broadcast_call);
	
	etimer_set(&et, CLOCK_SECOND * 2);
	PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));
	
	collect_open(&tc, 135, COLLECT_ROUTER, &collect_callbacks);
	
	/* Set Sink */
	if(rimeaddr_node_addr.u8[0] == 80) {
		printf("I am sink\n");
		collect_set_sink(&tc, 1);
	}
	
	etimer_set(&et, CLOCK_SECOND * 2);
	PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));
	
	mesh_open(&mesh, 120, &callbacks);	
	
	while(1) {
		
	/* Send collect message every 2 - 3 seconds */
	etimer_set(&et, CLOCK_SECOND * 2 + random_rand() % (CLOCK_SECOND * 1));
	PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));
	
	collect_send(&tc, 5);
	
	parent = collect_parent(&tc);
	if(!rimeaddr_cmp(parent, &oldparent)) {
		if(!rimeaddr_cmp(&oldparent, &rimeaddr_null)) {
		  printf("#L %d 0\n", oldparent.u8[0]);
		}
		if(!rimeaddr_cmp(parent, &rimeaddr_null)) {
		  printf("#L %d 1\n", parent->u8[0]);
		}
		rimeaddr_copy(&oldparent, parent);
	}
	
	/* Remove node or decrease TTL*/
	for(node = list_head(node_addresses); node != NULL; node = list_item_next(node)) {
		if(node->ttl == 0){
			list_remove(node_addresses, node);
		} else {
			node->ttl--;
		}
	}

	/* Send a broadcast every 1 - 2 seconds */
	etimer_set(&et, CLOCK_SECOND * 1 + random_rand() % (CLOCK_SECOND * 1));
	PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));

	/* Prepare message and send broadcast */
	memcpy(&(msg.seqno), &(seqno), sizeof(seqno));
	packetbuf_copyfrom(&msg, sizeof(struct broadcast_message));
	broadcast_send(&broadcast);
	
    /* Send data to sink */
    if(seqno >= MAX_PACKETS){
		static rimeaddr_t sink_addr;
		static struct neighbor *n;
		struct broadcast_message sink_message;

		// Set sink adress
		sink_addr.u8[0] = 80;
		sink_addr.u8[1] = 135;
		
		/* If Sink, print out edge instead of sending (to Sink)*/
		if(rimeaddr_node_addr.u8[0] == 80) {
			for(n = list_head(neighbors_list); n != NULL; n = list_item_next(n)) {
				printf("edge::%d.%d::%d.%d::%d::%d::%d::\n",
				n->addr.u8[0],
				n->addr.u8[1],
				rimeaddr_node_addr.u8[0],
				rimeaddr_node_addr.u8[1],
				(n->recv_count * 100) / seqno,
				n->rssi,
				n->lqi);
				n->recv_count = 0;
			}
		}
		else {
			/* Send each neighbordata to sink*/
			for(n = list_head(neighbors_list); n != NULL; n = list_item_next(n)) {
				etimer_set(&et, CLOCK_SECOND * 1 + random_rand() % (CLOCK_SECOND * 1));
				PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));
				memcpy(&(sink_message.seqno), &(seqno), sizeof(seqno));
				sink_message.n = *n;
				rimeaddr_copy(&sink_message.own_addr, &rimeaddr_node_addr);
				packetbuf_copyfrom(&sink_message, sizeof(sink_message));
				mesh_send(&mesh, &sink_addr);
				n->recv_count = 0;
			}
		}
		// Reset everything
		seqno = 1;
		for(n = list_head(neighbors_list); n != NULL; n = list_item_next(n)){
			list_remove(neighbors_list, n);
			memb_free(&neighbors_memb,n);
		}
	}
	seqno++;
  }
  PROCESS_END();
}
