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


#define BAUDRATE B57600
#define BAUDRATE_S "57600"
#ifdef linux
#define MODEMDEVICE "/dev/ttyS0"
#else
#define MODEMDEVICE "/dev/com1"
#endif /* linux */

#define SLIP_END     0300
#define SLIP_ESC     0333
#define SLIP_ESC_END 0334
#define SLIP_ESC_ESC 0335

#define CSNA_INIT 0x01
#define PORT 1337

#define BUFSIZE 1024
#define HCOLS 20
#define ICOLS 18

#define MODE_START_DATE	0
#define MODE_DATE	1
#define MODE_START_TEXT	2
#define MODE_TEXT	3
#define MODE_INT	4
#define MODE_HEX	5
#define MODE_SLIP_AUTO	6
#define MODE_SLIP	7
#define MODE_SLIP_HIDE	8

static unsigned char rxbuf[2048];

static int
usage(int result)
{
  printf("Usage: serialdump [-p PORT] [-h HOST] [-x] [-s[on]] [-i] [-bSPEED] [SERIALDEVICE]\n");
  printf("       -p to set port\n");
  printf("       -h to set host\n");
  printf("       -x for hexadecimal output\n");
  printf("       -i for decimal output\n");
  printf("       -s for automatic SLIP mode\n");
  printf("       -so for SLIP only mode (all data is SLIP packets)\n");
  printf("       -sn to hide SLIP packages\n");
  printf("       -T[format] to add time for each text line\n");
  printf("         (see man page for strftime() for format description)\n");
  return result;
}

static void
print_hex_line(unsigned char *prefix, unsigned char *outbuf, int index)
{
  int i;

  printf("\r%s", prefix);
  for(i = 0; i < index; i++) {
    if((i % 4) == 0) {
      printf(" ");
    }
    printf("%02X", outbuf[i] & 0xFF);
  }
  printf("  ");
  for(i = index; i < HCOLS; i++) {
    if((i % 4) == 0) {
      printf(" ");
    }
    printf("  ");
  }
  for(i = 0; i < index; i++) {
    if(outbuf[i] < 30 || outbuf[i] > 126) {
      printf(".");
    } else {
      printf("%c", outbuf[i]);
    }
  }
}

int main(int argc, char **argv)
{
  struct termios options;
  fd_set mask, smask;
  int fd;
  speed_t speed = BAUDRATE;
  char *speedname = BAUDRATE_S;
  char *device = MODEMDEVICE;
  char *timeformat = NULL;
  unsigned char buf[BUFSIZE], outbuf[HCOLS];
  unsigned char mode = MODE_START_TEXT;
  int nfound, flags = 0;
  unsigned char lastc = '\0';
  unsigned int port = 1337;
  struct hostent *host = gethostbyname("localhost");

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
      case 'b':
	/* set speed */
	if (strcmp(&argv[index][2], "38400") == 0) {
	  speed = B38400;
	  speedname = "38400";
	} else if (strcmp(&argv[index][2], "19200") == 0) {
	  speed = B19200;
	  speedname = "19200";
	} else if (strcmp(&argv[index][2], "57600") == 0) {
	  speed = B57600;
	  speedname = "57600";
	} else if (strcmp(&argv[index][2], "115200") == 0) {
	  speed = B115200;
	  speedname = "115200";
	} else {
	  fprintf(stderr, "unsupported speed: %s\n", &argv[index][2]);
	  return usage(1);
	}
	break;
      case 'x':
	mode = MODE_HEX;
	break;
      case 'i':
	mode = MODE_INT;
	break;
      case 's':
	switch(argv[index][2]) {
	case 'n':
	  mode = MODE_SLIP_HIDE;
	  break;
	case 'o':
	  mode = MODE_SLIP;
	  break;
	default:
	  mode = MODE_SLIP_AUTO;
	  break;
	}
	break;
      case 'T':
	if(strlen(&argv[index][2]) == 0) {
	  timeformat = "%Y-%m-%d %H:%M:%S";
	} else {
	  timeformat = &argv[index][2];
	}
	mode = MODE_START_DATE;
	break;
      default:
	fprintf(stderr, "unknown option '%c'\n", argv[index][1]);
	return usage(1);
      }
      index++;
    } else {
      device = argv[index++];
      if (index < argc) {
	fprintf(stderr, "too many arguments\n");
	return usage(1);
      }
    }
  }
  fprintf(stderr, "connecting to %s (%s) on %s::%d", device, speedname, host->h_name, port);

  fd = open(device, O_RDWR | O_NOCTTY | O_NDELAY | O_SYNC );
  if (fd <0) {
    fprintf(stderr, "\n");
    perror(device);
    exit(-1);
  }
  fprintf(stderr, " [OK]\n");

  if (fcntl(fd, F_SETFL, 0) < 0) {
    perror("could not set fcntl");
    exit(-1);
  }

  if (tcgetattr(fd, &options) < 0) {
    perror("could not get options");
    exit(-1);
  }
/*   fprintf(stderr, "serial options set\n"); */
  cfsetispeed(&options, speed);
  cfsetospeed(&options, speed);
  /* Enable the receiver and set local mode */
  options.c_cflag |= (CLOCAL | CREAD);
  /* Mask the character size bits and turn off (odd) parity */
  options.c_cflag &= ~(CSIZE|PARENB|PARODD);
  /* Select 8 data bits */
  options.c_cflag |= CS8;

  /* Raw input */
  options.c_lflag &= ~(ICANON | ECHO | ECHOE | ISIG);
  /* Raw output */
  options.c_oflag &= ~OPOST;

  if (tcsetattr(fd, TCSANOW, &options) < 0) {
    perror("could not set options");
    exit(-1);
  }
	int sock;
	struct sockaddr_in saddr;
	

	if((sock = socket(PF_INET,SOCK_STREAM,0)) == -1){
		perror("socket");
		exit(EXIT_FAILURE);
	}

	memset(&saddr, 0, sizeof(saddr));
	memcpy((char *) &saddr.sin_addr, (char *) host->h_addr, host->h_length);
	saddr.sin_family = AF_INET;
	saddr.sin_port = htons(port);
	
	if(connect(sock,(const struct sockaddr *)&saddr,sizeof(struct sockaddr_in)) < 0)
	{
		perror("connecting stream socket");
		exit(1);
	}

  /* Make read() return immediately */
/*    if (fcntl(fd, F_SETFL, FNDELAY) < 0) { */
/*      perror("\ncould not set fcntl"); */
/*      exit(-1); */
/*    } */

  FD_ZERO(&mask);
  FD_SET(fd, &mask);
  FD_SET(fileno(stdin), &mask);

  index = 0;
  for (;;) {
    smask = mask;
    nfound = select(FD_SETSIZE, &smask, (fd_set *) 0, (fd_set *) 0,
		    (struct timeval *) 0);
    if(nfound < 0) {
      if (errno == EINTR) {
	fprintf(stderr, "interrupted system call\n");
	continue;
      }
      /* something is very wrong! */
      perror("select");
      exit(1);
    }

    if(FD_ISSET(fileno(stdin), &smask)) {
      /* data from standard in */
      int n = read(fileno(stdin), buf, sizeof(buf));
      if (n < 0) {
	perror("could not read");
	exit(-1);
      } else if (n > 0) {
	/* because commands might need parameters, lines needs to be
	   separated which means the terminating LF must be sent */
/* 	while(n > 0 && buf[n - 1] < 32) { */
/* 	  n--; */
/* 	} */
	if(n > 0) {
	  int i;
	  /*	  fprintf(stderr, "SEND %d bytes\n", n);*/
	  /* write slowly */
	  for (i = 0; i < n; i++) {
	    if (write(fd, &buf[i], 1) <= 0) {
	      perror("write");
	      exit(1);
	    } else {
	      fflush(NULL);
	      usleep(6000);
	    }
	  }
	}
      } else {
	/* End of input, exit. */
	exit(0);
      }
    }

    if(FD_ISSET(fd, &smask)) {
      int i, j, n = read(fd, buf, sizeof(buf));
      if (n < 0) {
	perror("could not read");
	exit(-1);
      }

	/* Send buffer to plugin */
	// Print buffer
	for(i = 0; i < n; i++){
		printf("%c", buf[i]);
		/* write buffer to stream socket */
		if(send(sock,&buf[i],1,0) < 0)
		{
			perror("writing on stream socket");
			exit(EXIT_FAILURE);
		}
		fflush(NULL);
		fflush(stdout);
	}
    }
  }
  close(sock);
  return;
}
