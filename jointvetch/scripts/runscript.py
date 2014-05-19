import sys, os, subprocess, itertools, csv, copy, math, re

# params list : environmental stoch, hydrochory on/off, hydrochory level, adjustment level

###### Initial parameterization (large, uniform steps)
### environmental stochasticity
# params = [[1, 2, 3, 4, 5],
# 			["true"],
# 			[0.01],
# 			[0.165]]

### hydrochory
# params = [[2],
# 			["true"],
# 			[0, 0.0005, 0.005, 0.01, 0.05, 0.10, 0.20],
# 			[0.165]]

## Testing
# params = 	[[1, 2, 3, 4, 5, 6],
# 			["true"],
# 			[.0020],
# 			[1]]

###### Stampede parameterization 
## ideal world
# params =   [[1.0, 1.25, 1.5, 1.75, 2.0, 2.25, 2.5, 2.75, 3.0, 3.25, 3.5, 3.75, 4.0, 4.25, 4.5, 4.75, 5.0],
# 			["true", "false"],
# 			[0.0000, 0.0005, 0.0010, 0.0020, 0.0050, 0.0100, 0.0200, 0.0300, 0.0500, 0.1000],
# 			[0.165]]

params =   [[1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 5.0],
			["true", "false"],
			[0.0000, 0.0005, 0.0020, 0.0050, 0.0100, 0.0300, 0.0500, 0.1000],
			[0.165]]

## Seed implantation & corresponding aggregate percentages
# 0.0000	0% agg
# 0.0005    1% agg
# 0.0010    3% agg
# 0.0020    5% agg
# 0.0050   13% agg
# 0.0100   24% agg
# 0.0200   43% agg
# 0.0300   57% agg
# 0.0500   76% agg
# 0.1000   94% agg

def makeRunCommands( computer ): # 'server' or 'computer'
	redundantRuns = 150
	if (computer == 'server'):
		classpath = ("'../jointvetch/:"
					"../lib/mason/jar/mason.17.jar:"
					"../lib/geomason-1.5/geomason.1.5.jar:"
					"../lib/jts-1.13/lib/jts-1.13.jar:"
					"../lib/mason/itext-1.2.jar:"
					"../lib/mason/jcommon-1.0.21.jar:"
					"../lib/mason/jmf.jar:"
					"../lib/mason/portfolio.jar:"
					"../lib/mason/jfreechart-1.0.17.jar:"
					"../lib/commons-math3-3.2/commons-math3-3.2.jar:"
					"tmp'")
		with open("../tmp/runs.sh", 'w') as f:
			for i in range(0, redundantRuns):
				for p in itertools.product(*params):
					java_commands = 'java -Xmx6.4g -Xms4g jointvetch.HoltsCreek '
					param_list = ' '.join([str(v) for v in p]) + ' -quiet'
					output_file = ' >> /home/mcrawford/results/' + '_'.join([str(v) for v in p]) + '.txt'
					f.write(java_commands + param_list + output_file + '\n')
	
	elif (computer == 'stampede'):
		classpath = ("'../jointvetch/:"
					"../lib/mason/jar/mason.17.jar:"
					"../lib/geomason-1.5/geomason.1.5.jar:"
					"../lib/jts-1.13/lib/jts-1.13.jar:"
					"../lib/mason/itext-1.2.jar:"
					"../lib/mason/jcommon-1.0.21.jar:"
					"../lib/mason/jmf.jar:"
					"../lib/mason/portfolio.jar:"
					"../lib/mason/jfreechart-1.0.17.jar:"
					"../lib/commons-math3-3.2/commons-math3-3.2.jar:"
					"tmp'")
		numFiles = 50
		numRuns = reduce(lambda x, y: x*y, map(len, params)) * redundantRuns
		with open('../tmp/runs.sh', 'w') as f:
			for i in range(1, redundantRuns+1):
				for p in itertools.product(*params):
					java_commands = 'java -Xmx6.4g -Xms4g -classpath ' + classpath + ' jointvetch.HoltsCreek '
					param_list = ' '.join([str(v) for v in p]) + ' -quiet'
					output_file = ' >> results/' + '_'.join([str(v) for v in p]) + '.txt'
					f.write(java_commands + param_list + output_file + '\n')
		with open('../tmp/runs.sh', 'r') as r:
			for i in range(1, numFiles+1):
				batch_name = "../tmp/testrun0" if (i<10) else "../tmp/testrun"
				with open("".join(batch_name + str(i)), 'w') as f:
					for j in range(0, int(math.ceil(float(numRuns)/numFiles))):
						f.write(r.readline())
		makeSlurmLaunchers(numFiles)
		makeCompileCommand()
		makeQueue()
		os.remove('../tmp/runs.sh')

def makeCompileCommand():
	with open('compile.sh', 'w') as f:
		f.write("javac -d tmp -classpath " + classpath + " *.java")

def makeSlurmLaunchers(numFiles):
	with open('templateLauncher.slurm', 'r') as r:
		base = r.read()
		for i in range(1, numFiles):
			batch_number = ( '0' + str(i) ) if (i<10) else str(i)
			slurm_file = 'launcher' + batch_number + '.slurm'
			replace_string = 'testrun' + batch_number
			with open('../tmp/' + slurm_file, 'w') as w:
				w.write(re.sub('testrun', replace_string, base))

def makeQueue():
	with open('../tmp/queue.sh', 'w') as w:
		w.write(("for myrun in 'ls | grep launcher'\n"
				'do\n'
   				'\t sbatch $myrun\n'
				'done\n'))				

def createPath( path ):
	if not os.path.isdir(path):
		os.mkdir(path)

def main( argv ):
	# makeRunCommands('server')
	makeRunCommands('stampede')

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

