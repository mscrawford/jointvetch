require(shiny)
require(shinyIncubator)

shinyUI(fluidPage(title="Aeschynomene",

    tags$head(tags$link(rel="stylesheet", type="text/css",
        href="jointvetch.css")),

    progressInit(),

    headerPanel(""),

    sidebarLayout(
        sidebarPanel(HTML(paste0("<table border=0>",
        "<tr><td>",
        "<div id=SJV>Aeschynomene</div>",
        "<div class=creds>",
        "<span>",
        "Michael Crawford, ",
        "Stephen Davies, ",
        "Alan Griffith</span><br/>",
        "University of Mary Washington</div></td>",
        "<td><img src=\"sjv.jpg\" width=\"80px\" style=\"margin-left:10px;margin-right:10px;\"/></td></tr></table>")),
            h4("Simulation parameters"),
            fluidRow(
            numericInput("envStochMax","Environmental stochasticity max",
                value=2.0,min=0,max=8,step=.1),
            numericInput("impRate","Implantation rate",
                value=0.002,min=0,max=1,step=.0005),
            numericInput("adjFact","Adjustment factor",
                value=0.10,min=0,max=.4,step=.01),
            checkboxInput("hydrochory","Enable hydrochory"),
            radioButtons("seedType",label="",
                choices=c("Random seed"="rand",
                    "Specific seed"="specific"),
                selected="rand",
                inline=TRUE),
            conditionalPanel(condition="input.seedType == 'specific'",
                numericInput("seed","Seed",value=0)),
            numericInput("maxYrs","(Max) number of years",
                value=5,min=1,step=1)
            ),
            actionButton("runsim",label="Run sim"),
            htmlOutput("log")
        ),
        mainPanel(
            tabsetPanel(id="view",selected="Time series",
                tabPanel("Time series",
                    plotOutput("timeSeriesPlots",height="1400px")),
                tabPanel("Map",
                    verticalLayout(
                        imageOutput("animationImage",width="100%",height="100%"),
                        uiOutput("animationControl")))
#                    sliderInput("animation","Year",min=1,max=5,step=1,value=1,
#                        animate=animationOptions(interval=500,loop=FALSE)))
            )
        )
    )
))


