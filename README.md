# README #

## Implementation ##

* This is an attempt to solve the Santa Claus Problem from William Stallings’s "Operating Systems" using Scala and Akka.
* This implementation will be based on the solution found in Allen B. Downey's "The Little Book of Semaphores" (http://greenteapress.com/semaphores/)
* Actual Scala code is based on https://github.com/remeniuk/scala-santa-claus/blob/master/src/main/scala/com/vasilrem/santa/actors/SantaClausSimulation.scala
* Progress with the help of senia: http://stackoverflow.com/questions/27438110/scala-actors-vs-akka-actors

### Problem Statement ###

Santa Claus is sleeping in his shop at the North Pole and can only be awakened by all 9 reindeer or a group of 3 elves:
* All 9 reindeer are back from their vacation
	* All reindeer wait until the last one arrives, whom wakes up Santa
* 3 of the elves. 
	* While 3 elves visit Santa, the rest must wait for those elves to return
* 3 elves are waiting and last reindeer is back, Santa prioritizes reindeer

Additional specification:
* After the ninth reindeer arrives, Santa must invoke prepareSleigh, and then all nine reindeer must invoke getHitched.
* After the third elf arrives, Santa must invoke helpElves. Concurrently, all three elves should invoke getHelp.
* All three elves must invoke getHelp before any additional elves enter(increment the elf counter).
