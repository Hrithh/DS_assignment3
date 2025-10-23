# Paxos-Based Council Election System – DS Assignment 3

This project implements a Paxos-based distributed consensus system using Java. The system simulates a network of Council Members (nodes) that communicate using the Paxos algorithm to elect a Council President. Each node may behave under different reliability profiles (e.g., reliable, failure-prone, latent).

---

## Requirements

- **Java 11.0.28 (LTS)**
- **Maven 3.9.9**
- Git Bash (for Windows users) — required to execute run_tests.sh
- vendor: Eclipse Adoptium

---

## Dependencies

- Managed via pom.xml:
- gson (Google JSON)
- Java standard libraries (no external REST frameworks used)

## How to Run the System (Manual)

Make sure you're inside the `DS_AT3` project root.

### 1. **Compile**

`mvn clean compile`

### 2. **Start Council Members**

Open separate terminals for each member  
`-Dexec.mainClass=au.edu.adelaide.ds.assignment3.CouncilMember -Dexec.args="M1 --profile reliable"`  
`-Dexec.mainClass=au.edu.adelaide.ds.assignment3.CouncilMember -Dexec.args="M2 --profile standard"`  
`-Dexec.mainClass=au.edu.adelaide.ds.assignment3.CouncilMember -Dexec.args="M3 --profile failure"`  

You can use any combination of member IDs and profiles:
- reliable
- standard
- latent
- failure

### 3. **Trigger a Proposal(Manual)**

`mvn exec:java -Dexec.mainClass=au.edu.adelaide.ds.assignment3.ProposerClient -Dexec.args="M1 network.config"`

---

## How to Run the System (Automated)
A Bash script (run_tests.sh) is provided to automatically launch all 9 Council Members, trigger proposals, simulate failures, and capture logs for each scenario.  
You must use Git Bash or WSL to run the script.

### 1. **Compile**

`mvn clean compile`

### 2. **Execute Script**

`./run_test.sh`

You can choose option below:
- `./run_test.sh 1`
  (Ideal network (all reliable)
- `./run_test.sh 2`
  (Concurrent proposals (M1 vs M8))
- `./run_test.sh 3a`
  (Fault tolerance mix; M4 proposes)
- `./run_test.sh 3b`
  (Fault tolerance mix; M2 (latent) proposes)
- `./run_test.sh 3c`
  (Fault tolerance mix; M3 proposes then crashes)
- `./run_test.sh all`
  (All the test above)

### 3. **Clean logs**

`./run_test.sh clean`