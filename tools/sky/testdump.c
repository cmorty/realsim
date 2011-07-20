#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <termios.h>
#include <unistd.h>
#include <errno.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h>
#include <signal.h>
#include <netinet/tcp.h>

#define BUFSIZE 1024*16
#define PORT 1337


int main(int argc, char **argv)
{
	int sock;
	struct sockaddr_in saddr;
	struct hostent *host;
	unsigned char buf[BUFSIZE];
	FILE *fp;
	int i;
	
	if(argc < 2){
		printf("Usage: testdump datadumpfile\n");
		return;
	}

	if((sock = socket(PF_INET,SOCK_STREAM,0)) == -1){
		perror("socket");
		exit(EXIT_FAILURE);
	}
	host = gethostbyname("localhost");
	memset(&saddr, 0, sizeof(saddr));
	memcpy((char *) &saddr.sin_addr, (char *) host->h_addr, host->h_length);
	saddr.sin_family = AF_INET;
	saddr.sin_port = htons(PORT);
	
	if(connect(sock,(const struct sockaddr *)&saddr,sizeof(struct sockaddr_in)) < 0)
	{
		perror("connecting stream socket");
		exit(1);
	}

	/* Read from file*/
	fp = fopen (argv[1],"r");

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
