import sys, os, subprocess, itertools, csv, copy, math

# params list : environmental stoch, hydrochory on/off, hydrochory level, seed bank level (not currently used)

## Hydrochory
# params = 	[[2],
# 	 		["true", "false"],
# 	 		[0.0005,0.0010,0.0020,0.0050,0.0100,0.0200,0.0300,0.0500,0.1000],
# 	 		[0]]

## Environmental Stochasticity
# params = [[1, 1.5, 2, 2.5, 3, 3.5, 4, 4.5, 5, 5.5, 6],
# 			["true"],
# 			[0.0005],
# 			[0]]

## Adjustment Factor
# params = 	[[2],
# 	 		["true"],
# 	 		[0.0005],
# 	 		[0],
# 	 		[0.175, 0.1775, 0.180, 0.1825, 0.185]]

def makeRunCommands( computer ): # 'server' or 'computer'
#	# -classpath "/home/mcrawford/jointvetch/jointvetch/:/home/mcrawford/jointvetch/lib/mason/jar/mason.17.jar:/home/mcrawford/jointvetch/lib/geomason-1.5/geomason.1.5.jar:/home/mcrawford/jointvetch/lib/jts-1.13/lib/jts-1.13.jar:/home/mcrawford/jointvetch/lib/mason/itext-1.2.jar:/home/mcrawford/jointvetch/lib/mason/jcommon-1.0.21.jar:/home/mcrawford/jointvetch/lib/mason/jmf.jar:/home/mcrawford/jointvetch/lib/mason/portfolio.jar:/home/mcrawford/jointvetch/lib/mason/jfreechart-1.0.17.jar:/home/mcrawford/jointvetch/lib/commons-math3-3.2/commons-math3-3.2.jar:/tmp"
	if (computer == 'server'):
		numRuns = 30
		commandList = 'java -Xmx16g jointvetch.HoltsCreek '
		f = open("../tmp/runs.sh", 'w')
		for i in range(1, numRuns+1):
			for p in itertools.product(*params):
				f.write(commandList + ' '.join([str(v) for v in p] + ["-quiet", ">>","/home/mcrawford/results/"]) + '_'.join([str(v) for v in p]) + "\n")
		f.close()

	elif (computer == 'stampede'):
		numRuns = 300
		numFiles = 50
		numCommands = 0

		commandList = 'java -Xmx16g -classpath "../jointvetch/:../lib/mason/jar/mason.17.jar:../lib/geomason-1.5/geomason.1.5.jar:../lib/jts-1.13/lib/jts-1.13.jar:../lib/mason/itext-1.2.jar:../lib/mason/jcommon-1.0.21.jar:../lib/mason/jmf.jar:../lib/mason/portfolio.jar:../lib/mason/jfreechart-1.0.17.jar:../lib/commons-math3-3.2/commons-math3-3.2.jar" jointvetch.HoltsCreek '
		f = open("../tmp/runs.sh", 'w')
		for i in range(1, numRuns+1):
			for p in itertools.product(*params):
				f.write(commandList + ' '.join([str(v) for v in p] + ["-quiet", ">>","results/"]) + '_'.join([str(v) for v in p]) + ".txt \n")
				numCommands = numCommands+1
		f.close()

		r = open("../tmp/runs.sh", 'r')
		for i in range(1, numFiles+1):
			if (i<10):
				f = open("".join("../tmp/testrun0" + str(i)), 'w')
				for j in range(0, int(math.ceil(float(numCommands)/numFiles))):
					f.write(r.readline())
				f.close()
			else:
				f = open("".join("../tmp/testrun" + str(i)), 'w')
				for j in range(0, int(math.ceil(float(numCommands)/numFiles))):
					f.write(r.readline())
				f.close()
		r.close()

def createPath( path ):
	if not os.path.isdir(path):
		os.mkdir(path)

def main( argv ):
	makeRunCommands('server')

class cd:
	def __init__(self, newPath):
		self.newPath = newPath
	def __enter__(self):
		self.savedPath = os.getcwd()
		os.chdir(self.newPath)
	def __exit__(self, etype, value, traceback):
		os.chdir(self.savedPath)

if __name__ == "__main__":
	main(sys.argv)
