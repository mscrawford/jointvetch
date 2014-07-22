jointvetch
==========
An agent-based, pattern-oriented, and GIS-based simulation of Aeschynomene virginica, or Sensitive joint-vetch, in its tidal marshland habitat of Holts Creek, VA.

relative classpath to jars: 

/jointvetch/jointvetch/
/jointvetch/lib/mason/jar/mason.17.jar
/jointvetch/lib/geomason-1.5/geomason.1.5.jar
/jointvetch/lib/jts-1.13/lib/jts-1.13.jar
/jointvetch/lib/mason/itext-1.2.jar
/jointvetch/lib/mason/jcommon-1.0.21.jar
/jointvetch/lib/mason/jmf.jar
/jointvetch/lib/mason/portfolio.jar
/jointvetch/lib/mason/jfreechart-1.0.17.jar
/jointvetch/lib/commons-math3-3.2/commons-math3-3.2.jar


To compile, cd to "jointvetch," then:

javac -classpath ../lib/mason/jar/mason.17.jar:../lib/geomason-1.5/geomason.1.5.jar:../lib/jts-1.13/lib/jts-1.13.jar:../lib/mason/itext-1.2.jar:../lib/mason/jcommon-1.0.21.jar:../lib/mason/jmf.jar:../lib/mason/portfolio.jar:../lib/mason/jfreechart-1.0.17.jar:../lib/commons-math3-3.2/commons-math3-3.2.jar *.java

To run, cd to "jointvetch," then:

java -classpath ../jointvetch:../lib/mason/jar/mason.17.jar:../lib/geomason-1.5/geomason.1.5.jar:../lib/jts-1.13/lib/jts-1.13.jar:../lib/mason/itext-1.2.jar:../lib/mason/jcommon-1.0.21.jar:../lib/mason/jmf.jar:../lib/mason/portfolio.jar:../lib/mason/jfreechart-1.0.17.jar:../lib/commons-math3-3.2/commons-math3-3.2.jar:.. -Xmx8g jointvetch.HoltsCreek envStochMax hydrochoryBool implantationRate adjustmentFactor

For instance:

java -classpath ../jointvetch:../lib/mason/jar/mason.17.jar:../lib/geomason-1.5/geomason.1.5.jar:../lib/jts-1.13/lib/jts-1.13.jar:../lib/mason/itext-1.2.jar:../lib/mason/jcommon-1.0.21.jar:../lib/mason/jmf.jar:../lib/mason/portfolio.jar:../lib/mason/jfreechart-1.0.17.jar:../lib/commons-math3-3.2/commons-math3-3.2.jar:.. -Xmx8g jointvetch.HoltsCreek .2 true .08 .3
