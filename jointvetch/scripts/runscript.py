import sys, os, subprocess, itertools, csv, copy, math

## Seed bank
# params = 	[[2, 5],
# 	 		[0.33],
# 	 		["true"],
# 	 		[0.0010],
# 	 		[0, 0.039, 0.1, 0.2, 0.3, 0.4, 0.5]]

## Hydrochory
params = 	[[2],
	 		[0.33],
	 		["true", "false"],
	 		[0.0005,0.0010,0.0020,0.0050,0.0100,0.0200,0.0300,0.0500,0.1000],
	 		[0.039]]

## Environmental Stochasticity

## Adjustment Factor

def makeRunCommands():

	##################
	# single file - Cluster
	##################

	# numRuns = 100
	# commandList = 'java -Xmx16g -Xshare:off jointvetch.HoltsCreek '
	# f = open("../tmp/runs.sh", 'w')
	# for i in range(1, numRuns+1):
	# 	for p in itertools.product(*params):
	# 		f.write(commandList + ' '.join([str(v) for v in p] + [">>","/home/mcrawford/results/"]) + '_'.join([str(v) for v in p]) + "\n")
	# f.close()

	##################
	## multiple files - Supercomputer
	##################

	numRuns = 300
	numFiles = 50
	numCommands = 0

	commandList = 'java -Xmx16g -classpath ../MASON/jar/mason.17.jar:../jointvetch/:../MASON/:../MASON/geomason.1.5.jar:../MASON/itext-1.2.jar:../MASON/jcommon-1.0.16.jar:../MASON/jfreechart-1.0.13.jar:../MASON/jmf.jar:../MASON/jts-1.11.jar:tmp jointvetch.HoltsCreek '
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

def createPath(path):
	if not os.path.isdir(path):
		os.mkdir(path)

def main(argv):
	makeRunCommands()

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
