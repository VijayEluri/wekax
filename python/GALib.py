from random import random,shuffle
class Chromosome(object):
	gene=None
	def __init__(self,gene=[]):
		self.gene=gene
	def clone(self):
		return self.__class__(self.gene[:])
	def fitness(self):
		raise NotImplementedError()
	def crossover(a,b):
		raise NotImplementedError()
	def __add__(this,that):
		return this.crossover(that)
	def mutation(self):
		raise NotImplementedError()
	def optimize(self):
		raise NotImplementedError()
class Organism(object):
	chromosome=None
	fitness=None
	def __init__(self,chromosome=[]):
		self.chromosome=chromosome
	def best(self):
		return self.chromosome[self.fitness.index(max(self.fitness))].clone()
	def selection(self):
		l,s=len(self.chromosome),sum(self.fitness)
		p=l/s
		proportion=map(lambda x:int(round(x*p)),self.fitness)
		s=sum(proportion)
		if s<l:proportion[proportion.index(max(proportion))]+=l-s
		elif s>l:
			s-=l
			for p in sorted(proportion):
				if p==0:continue
				if p<s:
					s-=p
					proportion[proportion.index(p)]=0
				else:
					proportion[proportion.index(p)]-=s
					break
		for i in range(l):
			self.chromosome+=[self.chromosome[i] for j in range(proportion[i])]
		self.chromosome=self.chromosome[l:]
	def crossover(self,probability):
		l=len(self.chromosome)
		shuffle(self.chromosome)
		for i in range(1,l,2):
			if random()<probability:
				self.chromosome[i-1:i+1]=self.chromosome[i-1]+self.chromosome[i]
	def mutation(self,probability):
		for i in range(len(self.chromosome)):
			if random()<probability:
				self.chromosome[i].mutation()
	def elitism(self,chromosome):
		self.chromosome[self.fitness.index(min(self.fitness))]=chromosome
	def optimize(self):
		for chromosome in self.chromosome:chromosome.optimize()
		self.fitness=[chromosome.fitness() for chromosome in self.chromosome]
