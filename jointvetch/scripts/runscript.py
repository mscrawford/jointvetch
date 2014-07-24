import sys, os, subprocess, itertools, csv, copy, math, re, random

# params list : environmental stoch, hydrochory on/off, hydrochory level, adjustment level

## ideal world
# params =   [[1.0, 1.25, 1.5, 1.75, 2.0, 2.25, 2.5, 2.75, 3.0, 3.25, 3.5, 3.75, 4.0, 4.25, 4.5, 4.75, 5.0],
# 			["true", "false"],
# 			[0.0000, 0.0005, 0.0010, 0.0020, 0.0050, 0.0100, 0.0200, 0.0300, 0.0500, 0.1000],
# 			[0.165]]

# realisitic first set
# params =   [[1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 5.0],
# 			["true", "false"],
# 			[0.0000, 0.0005, 0.0020, 0.0050, 0.0100, 0.0300, 0.0500, 0.1000],
# 			[0.165]]

params =   [[1.0],
			["true", "false"],
			[0.0000, 0.0005, 0.0020, 0.0050, 0.0100, 0.0300, 0.0500, 0.1000],
			[ 0.155, 0.160, 0.165, 0.170, 0.175, 0.180, 0.190]]

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

base_classpath = ['../jointvetch/',
				'../lib/mason/jar/mason.17.jar',
				'../lib/geomason-1.5/geomason.1.5.jar',
				'../lib/jts-1.13/lib/jts-1.13.jar',
				'../lib/mason/itext-1.2.jar',
				'../lib/mason/jcommon-1.0.21.jar',
				'../lib/mason/jmf.jar',
				'../lib/mason/portfolio.jar',
				'../lib/mason/jfreechart-1.0.17.jar',
				'../lib/commons-math3-3.2/commons-math3-3.2.jar']

def makeLaunchFiles( computer ): # 'server' or 'computer'
	redundantRuns = 50
	maxNumFiles = 50	
	
	if (computer == 'server'):
		with open("../tmp/runs.sh", 'w') as f:
			for i in range(0, redundantRuns):
				for p in itertools.product(*params):
					java_commands = 'java -Xmx6.4g jointvetch.HoltsCreek '
					param_list = ' '.join([str(v) for v in p]) + ' -quiet -seed ' + str(random.randint(0, sys.maxint)) + ' '
					output_file = ' >> /home/mcrawford/results/' + '_'.join([str(v) for v in p]) + '.txt'
					f.write(java_commands + param_list + output_file + '\n')

	elif (computer == 'stampede'):
		base_classpath.append('/work/02172/mcrawfor/sjv/run/compiled')
		classpath = "'" + ':'.join(base_classpath) + "'"
		realNumFiles = 0
		numRuns = reduce(lambda x, y: x*y, map(len, params)) * redundantRuns
		commands = []
		
		for i in range(0, redundantRuns):
			for p in itertools.product(*params):
				java_commands = 'java -classpath ' + classpath + ' jointvetch.HoltsCreek '
				param_list = ' '.join([str(v) for v in p]) + ' -quiet -seed ' + str(random.randint(0, sys.maxint)) + ' '
				output_file = ' >> /work/02172/mcrawfor/sjv/run/results/' + '_'.join([str(v) for v in p]) + '.txt'
				commands.append(java_commands + param_list + output_file + '\n')
		
		for i in range(1, maxNumFiles+1):
			batch_name = ("../../tmp/testrun0" if (i < 10) else "../../tmp/testrun") + str(i)
			j = 0
			with open(batch_name, 'w') as f:
				while (j < int(math.ceil(float(numRuns)/maxNumFiles)) and len(commands) > 0):
					f.write(commands.pop())
					j+=1
			if (j == 0):
				os.remove(batch_name)
			else:
				realNumFiles+=1

		makeSlurmLaunchers(realNumFiles)
		makeCompileCommandScript(classpath)
		makeQueueScript()
		package()

def makeSlurmLaunchers(realNumFiles):
	with open('templateLauncher.slurm', 'r') as r:
		base = r.read()
		for i in range(1, realNumFiles+1):
			batch_number = ( '0' + str(i) ) if (i < 10) else str(i)
			slurm_file = 'launcher' + batch_number + '.slurm'
			replace_string = 'testrun' + batch_number
			with open('../../tmp/' + slurm_file, 'w') as w:
				w.write(re.sub('testrun', replace_string, base))

def makeCompileCommandScript(classpath):
	compileLocation = '/work/02172/mcrawfor/sjv/run/compiled'
	with open('../../tmp/compile.sh', 'w') as f:
		f.write('export _JAVA_OPTIONS="-Xmx1G"\n')
		f.write('javac -d ' + compileLocation + ' -classpath ' + classpath + ' *.java')

def makeQueueScript():
	with open('../../tmp/queue.sh', 'w') as w:
		w.write(('#!/bin/csh\n'
				"foreach myrun (`ls | grep launcher`)\n"
   				'\t sbatch $myrun\n'
				'end\n'))		

def package():
	with cd("../"):
		os.system('cp *.java ../tmp')
	with cd('../../tmp'):
		os.system('tar -cvf Archive.tar *')		

def createPath( path ):
	if not os.path.isdir(path):
		os.mkdir(path)

def main( argv ):
	# makeLaunchFiles('server')
	makeLaunchFiles('stampede')

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

