library(raster)
library(rgdal)

options(digits=22)
path = "../jointvetch/data/shapefiles/"

initialPopulations <- function() {
    asc.rg <- raster(paste0(path,"waterbody_raster.asc"))
    riverFlow.rg <- readOGR(path, "riverFlow")
    pampoint.rg <- readOGR(path, "Pampoint_All")
    
    image(asc.rg, col=terrain.colors(256), asp=1, main="------")
    lines(riverFlow.rg, asp=1, col="darkblue")
    points(pampoint.rg, asp=1, cex=.001, pch=20, col="red")
}

rasterCells <- function() {
    waterbody <- raster(paste0(path,"waterbody_raster.asc"))
    plants <- read.table("PLANT_COORDS.txt", sep=",", col.names=c("X", "Y"),
        stringsAsFactors=FALSE)
    colors <- extract(waterbody, cellFromXY(waterbody, plants))
    cat(colors)
}

plants <- function(plants.df) {
    plants <- read.table("PLANT_COORDS.txt", sep=",", col.names=c("X", "Y"),
        stringsAsFactors=FALSE)
    plot.plants(plants)
}

plot.plants <- function(plants.df, simtag) {
    riverFlow.rg <- readOGR(path, "riverFlow")
    asc.rg <- raster(paste0(path,"waterbody_raster.asc"))
    
    image(asc.rg, col=terrain.colors(256), asp=1, 
        xlab="", ylab="", xaxt="n", yaxt="n",
        main=paste0("Year ", plants.df[1,1], " (pop ",nrow(plants.df),")\n",
            "Sim #",simtag))
    lines(riverFlow.rg, asp=1, col="darkblue", cex=.001)
    points(plants.df$X, plants.df$Y, asp=1, cex=.5, pch=19, col="purple")
    legend("topleft",pch=19,col="purple",legend="Joint vetch")
}
