# TODO: Add comment
# 
# Author: morty
###############################################################################
library(ggplot2)
library(scales)

cstr <- function(...){return(paste(..., sep=''))}



setwd('/proj/i4work/morty/Contiki/eclipse/realsim_rep/wisebed')
#files <- c("2013-01-29-20:02:43realsim.out", "2013-01-31-17:26:57realsim.out")
files <- c("2013-02-05-18:18:20realsim.filt.par")

dt <- new.env()

for(file in files){
	ldt <-  read.table(file, header = FALSE, sep=" ",fill=TRUE,
		col.names=c("t", "mote", "dst", "src", "rssi", "lqi", "rcv", "drop", "dup" ),
		colClasses=c("character", "character", "character", "character", "character", "character", "character") )
	dt <- rbind(dt, ldt)
}



dt$t <- as.POSIXct(strptime(dt$t,"%Y-%m-%dT%H:%M:%S" ))
f <- function(t){dt[[t]] <- strtoi(dt[[t]], 16)}
for( x in c("dst","src", "rssi", "lqi", "rcv", "drop", "dup" )){
	dt[[x]] <- strtoi(dt[[x]], 16)
}



dt <- dt[dt$dst != "0",]
dt <- dt[dt$src != "0",]
dt <- dt[order(dt$t),]



for( x in c("dst","src", "mote")){
	dt[[x]] <- factor(dt[[x]])
}


# Filter out broken sources
dt <- dt[dt$src %in% levels(dt$dst),]

for( x in c("dst","src")){
	dt[[x]] <- factor(dt[[x]])
}





for(val in levels(dt$dst)[1:1]){
	#pdf(cstr(val, ".pdf"))
	tit <- cstr("Dst Node ", val)
	pld <- dt[dt$dst == val,]
	pld$src <- factor(pld$src)
	p <- ggplot(pld, aes(x=t, y=lqi, group=src, colour = src)) + geom_point(size = 1) + geom_path() + scale_x_datetime(major = "3 hour", minor="1 hour", format="%a %R ") + scale_y_continuous(formatter = "comma") + opts(title = tit)
	X11(type = "dbcairo")
	print(p)
	#dev.off()
	#ggsave(cstr(val, ".svg"), plot = p, height = 5, width=20)
	#
	#
}


#Langweilige verbindungen ausfiltern
dtsel <- new.env()
for(s in levels(dt$dst)){
	prefilt <-  dt[dt$dst == s,]
	print(s)
	for(d in levels(factor(prefilt$src))){
		res <- prefilt[prefilt$src == d ,]
		m <- min(res$lqi, +Inf, na.rm = TRUE)
#		print(m)
		if(!is.na(m)) if(m > 90) next
		print(cstr("A " , d))
		dtsel <- rbind(dtsel, res)
	}	
}



for(val in levels(dtsel$dst)[1:1]){
	#pdf(cstr(val, ".pdf"))
	tit <- cstr("Destination Node ", val)
	pld <- dtsel[dtsel$dst == val,]
	pld$src <- factor(pld$src)
	p <- ggplot(pld, aes(x=t, y=lqi, group=src, colour = src)) + geom_point(size = 1, aes(shape = src)) + geom_path() + scale_x_datetime(major = "3 hour", minor="1 hour", format="%a %R ") + scale_y_continuous(formatter = "comma") + opts(title = tit)
#	p <- ggplot(pld, aes(x=t, y=lqi, group=factor(src), colour = factor(pld$src))) + geom_point(size = 1) + geom_path() + 
		#scale_x_datetime(major = "3 hour", minor="1 hour", format="%a %R ") + scale_y_continuous(formatter = "comma") + opts(title = tit)
	X11(type = "dbcairo")
	print(p)
	#dev.off()
	#ggsave(cstr(val, ".svg"), plot = p, height = 5, width=20)
	#
	#
}

#Graph paper

start <- as.POSIXct(strptime('2013-02-05 19:30:00', '%Y-%m-%d %H:%M:%S'))
end <- as.POSIXct(strptime('2013-02-06 06:30:00', '%Y-%m-%d %H:%M:%S'))



t <-  dt[dt$dst == 100 & (dt$src == 31533 | dt$src == 11302 ),]
t2 <- t[t$t < start | t$t > end,]
filtf <- function(d){
	if(d < start) return("eavening")
	if(d > end) return ("morning")
	return(NA)
}
t2$tsort <- sapply(t2$t, filtf)

t <- t2

		
ggplot(t, aes(x=t, y=lqi, group=src, colour = src)) + geom_point(size = 4, aes(shape = src)) + geom_path() +  
		scale_x_datetime(breaks = date_breaks("1 hour"),minor_breaks = date_breaks("15 min"), labels = date_format("%a %R"))+
		 theme_bw() + facet_grid(. ~ tsort, scales = "free", space = "free")  +
		guides(colour = guide_legend("Source Node"), shape = guide_legend("Source Node")) + 
		labs(title = "Destination Node: 100", x = "Time", y = "LQI")



