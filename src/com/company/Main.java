package com.company;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Scanner;

class Elevator extends Thread {

    private int currentFloor;
    private int movement;
    private HashSet<Integer> route;
    private Core core;

    Elevator(Core core){
        super();
        route = new HashSet<>();
        currentFloor = 1;
        movement = 0;
        this.core = core;
    }

    public int getCurrentFloor(){
        return currentFloor;
    }

    public int getMovement(){
        return movement;
    }

    public void addRoutePoint(int floor){
        synchronized (this){
            if (this.movement == 1 && floor > this.currentFloor ||
                this.movement == -1 && floor < this.currentFloor ||
                this.movement == 0) {
                route.add(floor);
            }
        }

        if (route.size() == 1){ // first route point
            if (floor < currentFloor){
                movement = -1;
            }
            else{
                movement = 1;
            }
        }
    }

    public String drawRoute(){
        return route.toString();
    }

    public void goDown() throws InterruptedException {
        Thread.sleep(1000);
        this.currentFloor -= 1;
    }

    public void goUp() throws InterruptedException {
        Thread.sleep(1000);
        this.currentFloor += 1;
    }

    public void run(){
        try{
            while (true){ // one iteration = one floor change
                synchronized (this) {
                    if (route.contains(currentFloor)) { // drop off clients if necessary
                        Thread.sleep(1000);
                        route.remove(currentFloor);
                    }

                    if (movement == -1) { // lift primary direction is down
                        int[] clients = core.takeDownClientsByFloor(currentFloor); // take clients if necessary
                        if (clients.length != 0) {
                            Thread.sleep(1000);
                            for (int client : clients) {
                                route.add(client);
                            }
                        }
                        if (route.size() != 0) {
                            this.goDown();
                            continue;
                        }

                        boolean[] floorsWithClients = core.getDownClients();                // check primary direction floors
                        for (int i = 0; i < floorsWithClients.length && i < currentFloor; i++) { // if no more clients in lift
                            if (floorsWithClients[i]) {
                                route.add(i);
                            }
                        }

                        if (route.size() != 0) {
                            this.goDown();
                            continue;
                        }

                        // there are no more clients on lower floors, need to stop and update primary direction
                        movement = 0;
                    } else if (movement == 1) {
                        int[] clients = core.takeUpClientsByFloor(currentFloor); // take clients if necessary
                        if (clients.length != 0) {
                            Thread.sleep(1000);
                            for (int client : clients) {
                                route.add(client);
                            }
                        }
                        if (route.size() != 0) {
                            this.goUp();
                            continue;
                        }

                        boolean[] floorsWithClients = core.getUpClients();                // check primary direction floors
                        for (int i = currentFloor; i < floorsWithClients.length; i++) { // if no more clients in lift
                            if (floorsWithClients[i]) {
                                route.add(i);
                            }
                        }
                        if (route.size() != 0) {
                            this.goUp();
                            continue;
                        }

                        // there are no more clients on lower floors, need to stop and update primary direction
                        movement = 0;
                    } else {
                        boolean needToWorkFlag = false;

                        int []localDownClients = core.takeDownClientsByFloor(currentFloor);
                        if (localDownClients.length != 0){
                            for (int i : localDownClients){
                                route.add(i);
                                movement = -1;
                                needToWorkFlag = true;
                            }
                        }

                        if (needToWorkFlag){ // clients picked up and movement updated
                            continue;
                        }

                        int []localUpClients = core.takeDownClientsByFloor(currentFloor);
                        if (localDownClients.length != 0){
                            for (int i : localDownClients){
                                route.add(i);
                                movement = 1;
                                needToWorkFlag = true;
                            }
                        }

                        if (needToWorkFlag){ // clients picked up and movement updated
                            continue;
                        }

                        boolean[] upClients = core.getUpClients();
                        boolean[] downClients = core.getDownClients();

                        for (int i = 0; i < upClients.length; i++) {
                            if (upClients[i]) { // pick up lowest up client and set "up" primary direction
                                while (i > currentFloor){
                                    goUp();
                                }
                                while (i < currentFloor){
                                    goDown();
                                }
                                movement = 1;
                                needToWorkFlag = true;
                                break;
                            }
                        }
                        if (needToWorkFlag) { // clients picked up and movement updated
                            continue;
                        }

                        for (int i = downClients.length - 1; i >= 0; i--) {
                            if (downClients[i]) { // pick up highest up client and set "down" primary direction
                                while (i > currentFloor){
                                    goUp();
                                }
                                while (i < currentFloor){
                                    goDown();
                                }
                                movement = -1;
                                needToWorkFlag = true;
                                break;
                            }
                        }

                        if (needToWorkFlag) { // clients picked up and movement updated
                            continue;
                        }

                        // no clients now, wait for the first addClient function's notify
                        if (movement == 0)
                        {
                            wait();
                        }
                    }
                }

            }
        }
        catch (InterruptedException e) {
            return;
        }
    }
}

class SignalScanner extends Thread {

    private Core core;

    SignalScanner(Core core){
        super();
        this.core = core;
    }

    public void run(){
        Scanner in = new Scanner(System.in);
        while (true)
        {
            String line = in.nextLine();
            if (line.equals("0")){
                core.changeSignal(0); // translate exit signal to core
                return;
            }
        }
    }

}

class Generator extends Thread {

    private int floorsAmount;
    private Core core;

    Generator(Core core){
        this.floorsAmount = core.getFloorsAmount();
        this.core = core;
    }

    public void run(){
        try {
            while (true) {
                int source = (int) (Math.random() * this.floorsAmount + 1); // generation of integer number
                int target = (int) (Math.random() * this.floorsAmount + 1); // in [1, floorsAmount] range
                if (source == target){  // nobody has come to use the elevator
                    continue;
                }
                else{
                    core.addClient(source, target);
                }
                Thread.sleep(1000);
            }
        }
        catch (InterruptedException e)
        {
            return;
        }
    }

}

class Core extends Thread {
    private ArrayList<HashSet<Integer>> upClients;
    private ArrayList<HashSet<Integer>> downClients;
    ArrayList<Elevator> lifts;
    private int floorsAmount;
    private int signal; // 1 - keep working, 0 - exit

    Core(int floorsAmount, int liftsAmount){
        this.signal = 1;
        this.floorsAmount = floorsAmount;
        lifts = new ArrayList<>(liftsAmount);
        upClients = new ArrayList<>(floorsAmount);
        downClients = new ArrayList<>(floorsAmount);
        for (int i = 0; i < floorsAmount; i++){

            upClients.add(new HashSet<Integer>());
            downClients.add(new HashSet<Integer>());
        }
        for (int i = 0; i < liftsAmount; i++){
            lifts.add(i, new Elevator(this));
        }
    }

    public void run(){
        for (Elevator elevator : this.lifts){
            elevator.start();
        }
        SignalScanner signalScanner = new SignalScanner(this);
        Generator generator = new Generator(this);
        signalScanner.start();
        generator.start();
        while (signal != 0)
        {
            try {
                Thread.sleep(1000);
                this.draw();
            } catch (InterruptedException e) {}
        }
        signalScanner.interrupt();
        generator.interrupt();
        for (Elevator elevator : lifts){
            elevator.interrupt();
        }
    }

    public void draw(){
        int[] currentFloors = new int[lifts.size()];
        for (int i = 0; i < lifts.size(); i++){
            currentFloors[i] = lifts.get(i).getCurrentFloor();
        }

        for (int i = floorsAmount - 1; i >= 0; i--){
            System.out.print(i);
            System.out.print(": ");
            for (int j = 0; j < currentFloors.length; j++){
                if (i == currentFloors[j]){
                    System.out.print('X');
                }
                else{
                    System.out.print('-');
                }
            }
            System.out.print(' ');
            System.out.print(this.downClients.get(i));
            System.out.print(this.upClients.get(i));
            System.out.print('\n');
        }
        System.out.print("Lifts state:\n");
        for (Elevator elevator : lifts){
            System.out.print(elevator.getMovement());
            System.out.print(": ");
            System.out.print(elevator.drawRoute());
            System.out.print('\n');
        }

        System.out.print("------------------------------------\n");
    }

    public int getFloorsAmount(){
        return floorsAmount;
    }
    
    public boolean[] getUpClients(){
        boolean booleanArrayOfUpClients[] = new boolean[floorsAmount];
        synchronized (this.upClients) {
            for (int i = 0; i < floorsAmount; i++) {
                if (upClients.get(i).size()>0){
                    booleanArrayOfUpClients[i] = true;
                }
                else{
                    booleanArrayOfUpClients[i] = false;
                }
            }
        }
        return booleanArrayOfUpClients;
    }

    public boolean[] getDownClients(){
        boolean booleanArrayOfDownClients[] = new boolean[floorsAmount];
        synchronized (this.downClients) {
            for (int i = 0; i < floorsAmount; i++) {
                if (downClients.get(i).size() > 0){
                    booleanArrayOfDownClients[i] = true;
                }
                else{
                    booleanArrayOfDownClients[i] = false;
                }
            }
        }
        return booleanArrayOfDownClients;
    }

    public void addClient(int source, int target){
        source--;
        target--;
        if (source > target){
            synchronized (downClients) {
                downClients.get(source).add(target);
                /*if (downClients.get(source).size() == 1) { // first client on this floor
                    int closestLiftIndex = -1;
                    for (int i = 0; i < lifts.size(); i++) {
                        if (lifts.get(i).getMovement() == 0 || // lift checking
                                lifts.get(i).getCurrentFloor() > source && lifts.get(i).getMovement() == -1){
                            if (closestLiftIndex == -1) { // first available elevator case2
                                closestLiftIndex = i;
                                continue;
                            }
                            if (Math.abs(lifts.get(i).getCurrentFloor() - source) < // current elevator closer
                                    Math.abs(lifts.get(closestLiftIndex).getCurrentFloor() - source)){
                                closestLiftIndex = i;
                            }
                        }
                    }
                    if (closestLiftIndex != -1) { // there is available elevator for this client
                        synchronized (lifts.get(closestLiftIndex)) {
                            lifts.get(closestLiftIndex).addRoutePoint(source);
                            lifts.get(closestLiftIndex).notify();
                        }
                    }
                }*/
            }
        }
        if (source < target){
            synchronized (upClients) {
                upClients.get(source).add(target);
                /*if (downClients.get(source).size() == 1) { // first client on this floor
                    int closestLiftIndex = -1;
                    for (int i = 0; i < lifts.size(); i++) {
                        if (lifts.get(i).getMovement() == 0 || // lift checking
                                lifts.get(i).getCurrentFloor() < source && lifts.get(i).getMovement() == 1){
                            if (closestLiftIndex == -1) { // first available elevator case2
                                closestLiftIndex = i;
                                continue;
                            }
                            if (Math.abs(lifts.get(i).getCurrentFloor() - source) < // current elevator closer
                                    Math.abs(lifts.get(closestLiftIndex).getCurrentFloor() - source)){
                                closestLiftIndex = i;
                            }
                        }
                    }
                    if (closestLiftIndex != -1){ // there is available elevator for this client
                        synchronized (lifts.get(closestLiftIndex)) {
                            lifts.get(closestLiftIndex).addRoutePoint(source);
                            lifts.get(closestLiftIndex).notify();
                        }
                    }
                }*/
            }
        }
        for (Elevator elevator : lifts){
            synchronized (elevator) {
                elevator.notify();
            }
        }
    }

    public int[] takeUpClientsByFloor(int floor){
        synchronized (upClients) {
            int result[] = new int [upClients.get(floor).size()];
            int index = 0;
            for (int elem : upClients.get(floor)){
                result[index] = elem;
            }
            upClients.get(floor).clear();
            return result;
        }
    }

    public int[] takeDownClientsByFloor(int floor){
        synchronized (downClients) {
            int result[] = new int [downClients.get(floor).size()];
            int index = 0;
            for (int elem : downClients.get(floor)){
                result[index] = elem;
            }
            downClients.get(floor).clear();
            return result;
        }
    }

    public void changeSignal(int signal){
        this.signal = signal;
    }
}

public class Main {
    public static void main(String[] args) {
        Core core = new Core(10, 2);
        core.start();
        System.out.print("Main finished\n");
    }
}
