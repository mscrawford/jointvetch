
require(shiny)

JOINT.VETCH.ROOT <- "/home/stephen/muskrat/jointvetch"
CLASSES.DIR <- "/tmp/classes"

OUT.FILE <- "/tmp/output"
SIM.STATS.FILE <- "/tmp/sim_stats.csv"
CLUSTER.STATS.FILE <- "/tmp/cluster_stats.csv"
PLOT.SAVE.DIR <- "/tmp/plots"

REFRESH.PERIOD.MILLIS <- 500

libs <- c("mason/jar/mason.17.jar",
    "geomason-1.5/geomason.1.5.jar",
    "jts-1.13/lib/jts-1.13.jar",
    "mason/itext-1.2.jar",
    "mason/jcommon-1.0.21.jar",
    "mason/jmf.jar",
    "mason/portfolio.jar",
    "mason/jfreechart-1.0.17.jar",
    "commons-math3-3.2/commons-math3-3.2.jar")

classpath <- paste0(JOINT.VETCH.ROOT,"/jointvetch:",
    paste(JOINT.VETCH.ROOT,"lib",libs,sep="/",collapse=":"),":",
    CLASSES.DIR)



shinyServer(function(input,output,session) {

    # Per-session variables
    sim.started <- FALSE
    progress <- NULL
    simtag <- 0

    sim.stats <- function() {
        if (!file.exists(paste0(SIM.STATS.FILE,simtag))) {
            return(data.frame())
        }
        tryCatch({
            read.csv(paste0(SIM.STATS.FILE,simtag),header=TRUE,
                stringsAsFactors=FALSE)
        },error = function(e) return(data.frame())
        )
    }

    cluster.stats <- function() {
        if (!file.exists(paste0(CLUSTER.STATS.FILE,simtag))) {
            return(data.frame())
        }
        tryCatch({
            read.csv(paste0(CLUSTER.STATS.FILE,simtag),header=TRUE,
                stringsAsFactors=FALSE)
        },error = function(e) return(data.frame())
        )
    }

    seed <- function() {
        if (!file.exists(paste0(OUT.FILE,simtag))) {
            return(NA)
        }
        tryCatch({
            as.numeric(strsplit(readLines(
                paste0(OUT.FILE,simtag),n=1)[1],":")[[1]][2])
        },error = function(e) return(NA)
        )
    }

    output$timeSeriesPlots <- renderPlot({
        if (input$runsim < 1) return(NULL)
        isolate({
            if (!sim.started) {
                simtag <<- ceiling(runif(1,1,1e8))
                cat("starting sim!",simtag,"\n")
                progress <<- Progress$new(session,min=0,max=input$maxYrs+1)
                progress$set(message="Launching simulation...",value=0)
                lapply(c(paste0(SIM.STATS.FILE,simtag),
                    paste0(CLUSTER.STATS.FILE,simtag)),
                    function(file) if (file.exists(file)) file.remove(file))
                start.sim(input,simtag)
                progress$set(message="Initializing simulation...",value=1)
                sim.started <<- TRUE
            }
        })
        sim.stats.df <- sim.stats()
        cluster.stats.df <- cluster.stats()
        output$log <- renderText(HTML(paste0("<b>Log output:</b><br/>",
            "sim #",simtag,"<br/>",
            "seed: ",seed(),"<br/>")))
        maxYrs <- isolate(input$maxYrs)
        if (nrow(sim.stats.df) > 0) {
            progress$set("Running simulation...",
                detail=paste(max(sim.stats.df$year),"of",maxYrs,
                    "years"),
                value=1+max(sim.stats.df$year))
            if (max(sim.stats.df$year) == maxYrs) {
                progress$set("Done.",value=1+maxYrs)
                sim.started <<- FALSE
                if (!file.exists(paste0(PLOT.SAVE.DIR,simtag))) {
                    dir.create(paste0(PLOT.SAVE.DIR,simtag),
                        recursive=TRUE)
                }
                png(paste(paste0(PLOT.SAVE.DIR,simtag),"pop.png",
                    sep="/"))
                plot.time.serieses(input,sim.stats.df,cluster.stats.df,seed())
                dev.off()
                progress$close()
            } else {
                # Check output files again in a bit.
                invalidateLater(REFRESH.PERIOD.MILLIS,session)
            }
            plot.time.serieses(input,sim.stats.df,cluster.stats.df,seed())
        } else {
            invalidateLater(REFRESH.PERIOD.MILLIS,session)
            frame()
        }
    })
})

plot.time.serieses <- function(input,sim.stats.df,cluster.stats.df,seed) {
    isolate({
        sim.stats.df[setdiff(1:max(sim.stats.df$year),sim.stats.df$year),
            c("pop","env")] <- c(0,NA)
        par(mfrow=c(3,1))
        plot(sim.stats.df$year,sim.stats.df$pop,
            type="l",col="darkgreen",lwd=2,
            main="Total population",
            sub=paste0("(EnvStochMax=",input$envStochMax,", ",
                "Hydrochory=",input$hydrochory,", ",
                "ImpRate=",input$impRate,", ",
                "AdjFact=",input$adjFact,", ",
                "Seed=",seed,")"),
            ylab="Population (plants)",
            xlab="Year",
            xlim=c(0,input$maxYrs))
        plot(cluster.stats.df$year,cluster.stats.df$cluster.pop,
            type="p",col="blue",pch=20,
            main="Cluster analysis",
            ylab="Per-cluster populations",
            xlab="Year",
            xlim=c(0,input$maxYrs))
        plot(sim.stats.df$year,sim.stats.df$env,
            type="l",col="brown",lwd=2,
            main="Environmental history",
            ylab="Yearly environmental adjustment factor",
            xlab="Year",
            xlim=c(0,input$maxYrs))
    })
}

start.sim <- function(input,simtag) {
    setwd(paste0(JOINT.VETCH.ROOT,"/jointvetch"))
    isolate({
        system(paste("nice java -classpath ",classpath,
            "-Xmx8g jointvetch.HoltsCreek",
            input$envStochMax,
            input$hydrochory,
            input$impRate,
            input$adjFact,
            "-years",input$maxYrs,
            "-tag",simtag,
            "-verbose -quiet ",
            ifelse(input$seedType=="specific",paste("-seed",input$seed),""),
            ">",paste0(OUT.FILE,simtag),"&"))
    })
}

kill.all.sims <- function() {
    system("pkill -f jointvetch")
}
