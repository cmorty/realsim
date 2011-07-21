#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h>
#include <netinet/tcp.h>

#define BUFSIZE 1024*16

static int
usage(int result)
{
  printf("Usage: testdump [-p PORT] [-h HOST] [datadumpfile]\n");
  printf("       -p to set port\n");
  printf("       -h to set host\n");

  return result;
}


int main(int argc, char **argv)
{
	int sock;
	int port = 1337;
	struct sockaddr_in saddr;
	struct hostent *host = gethostbyname("localhost");
	unsigned char buf[BUFSIZE];
	FILE *fp;
	const char *filename;
	int i;
	
	  int index = 1;
	  while (index < argc) {
	    if (argv[index][0] == '-') {
	      switch(argv[index][1]) {
	      case 'p':
	    	  port = atoi(argv[++index]);
	    	  break;
	      case 'h':
	    	  if(argc <= index+1){
	    		  return usage(1);
	    	  }
	      	  if((host = gethostbyname(argv[++index])) == NULL){
	      		fprintf(stderr, "unknown host: %s\n", argv[index]);
	      		return usage(1);
	      	  }
	      	  break;
	      }
	    }

	    filename = argv[index];

	    index++;
	  }

	if(argc < 2){
		return usage(1);
	}

	if((sock = socket(PF_INET,SOCK_STREAM,0)) == -1){
		perror("socket");
		exit(EXIT_FAILURE);
	}
	fprintf(stderr, "connecting to %s::%d\n", host->h_name, port);

	memset(&saddr, 0, sizeof(saddr));
	memcpy((char *) &saddr.sin_addr, (char *) host->h_addr, host->h_length);
	saddr.sin_family = AF_INET;
	saddr.sin_port = htons(port);
	
	if(connect(sock,(const struct sockaddr *)&saddr,sizeof(struct sockaddr_in)) < 0)
	{
		perror("connecting stream socket");
		exit(1);
	}

	/* Read from file*/
	if((fp = fopen (filename,"r")) == NULL){
		perror("unknown file");
		exit(1);
	}

	int c;
	int k = 0;
	while ((c = fgetc(fp)) != EOF){
	   buf[k++] = c;
	}

	/* Send buffer to plugin */
	// Print buffer
	for(i = 0; i < k; i++){
		printf("%c", buf[i]);
		/* write buffer to stream socket */
		if(send(sock,&buf[i],1,0) < 0)
		{
			perror("writing on stream socket");
			exit(EXIT_FAILURE);
		}

		fflush(NULL);
		fflush(stdout);

		/* Wait 2 Seconds after each line */
		if(buf[i] == '\n'){
			sleep(2);
		}
	}
  close(sock);
  return;
}
