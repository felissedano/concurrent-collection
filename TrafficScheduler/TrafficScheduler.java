import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


public class TrafficScheduler {
    public static final int RIGHT = 1;
    public static final int LEFT = -1;

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Needs to have exactly 3 arguments");
            System.exit(1);
        }
        int n = 0;
        int s = 0;
        int d = 0;

        try{
            n = Integer.parseInt(args[0]);
            s = Integer.parseInt(args[1]);
            d = Integer.parseInt(args[2]);
        } catch (Exception e){
            System.err.println("Something wrong with the arguments");
            System.exit(1);
        }
        float bias = 1 / (float)d;
        bias = bias * 50;
        int maxEnter = n + (int) bias;

        new Thread(new CarGenerator(n, s, d, new StreetMonitor(n,s,d, maxEnter))).start();
    }

    
}

enum Direction{
    Right,
    Left;
}

class StreetMonitor{
    private int n; 
    private ReentrantLock[] segments; // just n
    private int maxEnter; // calculated in q2 main()
    private Semaphore rightSem; // for queuing the cars going to right side
    private Semaphore leftSem; // for going to the left side
    private AtomicInteger currentDirection; // currently allowed direction, could switch if the road is free
    private int numEntered; // number of car entered the segment
    private int numExit; // number of car exited the segment, if numExit = numEntered then the road is free at that instance
    private ReentrantLock inLock; //lock for going in and getting out. one must grab it before updating the road situation
    private Condition leftCondition; // to signal car on the right side can start heading to the left
    private Condition rightCondition; // vice versa



    public StreetMonitor(int n, int s, int d, int maxEnter){
        this.n = n;
        this.segments = new ReentrantLock[n];
        this.maxEnter = maxEnter;
        this.rightSem = new Semaphore(1,true);
        this.leftSem = new Semaphore(1, true);
        this.currentDirection = new AtomicInteger(TrafficScheduler.RIGHT);

        this.inLock = new ReentrantLock();
        this.leftCondition = inLock.newCondition();
        this.rightCondition = inLock.newCondition();


        for (int i = 0; i < segments.length; i++){
            segments[i] = new ReentrantLock();
        }

    }

    public void enter(Car car){
        Semaphore curSem;
        Condition myCond;

        // grab corresponding semaphore and condition base on direction
        if (car.getDirection() == Direction.Right){
            curSem = rightSem;
            // otherSem = leftSem;
            myCond = rightCondition;
        } else{
            curSem = leftSem;
            // otherSem = rightSem;
            myCond = leftCondition;
        }

        try {
            curSem.acquire(); // must acquire to to process the enter request. essentially a queue

            // if not same direction but road is free, but maybe there is a car of that direction that going to enter, 
            // so should wait and is it's still free, if yes then should be able to pass
            if (this.currentDirection.get() != car.getDirectionInt() & (numEntered == 0 || numEntered == numExit)){
                Thread.sleep(10); // wait a bit more to confirm
                inLock.lock();
                if (this.currentDirection.get() != car.getDirectionInt() & (numEntered == 0 || numEntered == numExit)) { // road is free
                    this.currentDirection.set(car.getDirectionInt());
                    // System.out.println(car.getCardId() + ", " + car.getDirectionInt() + " not my direction but road is free so why not");

                }
                inLock.unlock();

            }

            inLock.lock();
            if (this.currentDirection.get() == car.getDirectionInt()){
                // System.out.println(car.getCardId() + ", " + car.getDirectionInt() + "same direction");
                if(this.numEntered < maxEnter){
                    this.numEntered += 1;
                    // System.out.println(car.getCardId() + ", " + car.getDirectionInt() + " entered wiht numenter = " + numEntered);
                } else{
                    // System.out.println(car.getCardId() + ", " + car.getDirectionInt() + "max reached so should wait");
                    while (this.numEntered != 0) {
                        myCond.await(); //wait for the condition signaled by the car of opposite direction
                    }
                    this.numEntered += 1;
                }

            } else{
                // System.out.println(car.getCardId() + ", " + car.getDirectionInt() + "not the same direction so should wait");
                while (this.currentDirection.get() != car.getDirectionInt()) { //wait for the condition that direction changed
                    myCond.await();
                }
                this.numEntered += 1;

            }

            inLock.unlock();

            int segToEnter = car.getCurSegment();
            // try to get to the entry point
            segments[segToEnter].lock();


        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally{
            System.out.println("enter: " + car.getCardId() + ", " + car.getCurSegment());
            // System.out.println(car.getCardId() + ", " + car.getDirectionInt() + " releasing the semaphore");
            curSem.release(); // car is for sure entered and can let the next car in line to be processed.
        }


        
    }

    public void move(Car car){

        if (car.getDirectionInt() == TrafficScheduler.RIGHT) {
            // While not the right most segment
            while (car.getCurSegment() < n - 1) {
                int curSeg = car.getCurSegment();
                int nextSeg = car.getCurSegment() + car.getDirectionInt();
                this.segments[nextSeg].lock();
                // sleep to simulate moving to one segment to the other
                try {
                    Thread.sleep(car.getTimePerSegment());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                this.segments[curSeg].unlock();
                car.setCurSegment(nextSeg);
                // System.out.println("Car " + car.getCardId() + "moving from " + curSeg + " to " + nextSeg);
            }

            
        } else {
            while (car.getCurSegment() > 0) {
                int curSeg = car.getCurSegment();
                int nextSeg = car.getCurSegment() + car.getDirectionInt();
                this.segments[nextSeg].lock();
                // sleep to simulate moving to one segment to the other
                try {
                    Thread.sleep(car.getTimePerSegment());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                // free the previous segment
                this.segments[curSeg].unlock();
                car.setCurSegment(nextSeg);
                // System.out.println("Car " + car.getCardId() + "moving from " + curSeg + " to " + nextSeg);

            }
        }

        // Do the final unlock of the exit spot
        this.segments[car.getCurSegment()].unlock();

        int dirToFlip;
        Condition otherCond;
        // If dir is right, the should flip direction to left, notify cars on the left to pass
        if (car.getDirectionInt() == TrafficScheduler.RIGHT){
            dirToFlip = TrafficScheduler.LEFT;
            otherCond = leftCondition;
        } else {
            dirToFlip = TrafficScheduler.RIGHT;
            otherCond = rightCondition;
        }

        //exit the street
        inLock.lock();
        this.numExit +=1;
        // if it's last car to leave then reset the exit and enter num, and flip direction
        if(this.numExit == this.maxEnter) {
            this.numEntered = 0;
            this.numExit = 0;
            this.currentDirection.set(dirToFlip);
            otherCond.signal();
            // System.out.println("Im last to exit: " + car.getCardId() + " direction " + car.getDirectionInt());
            System.out.println("exit: " + car.getCardId() + ", " + car.getCurSegment());
        } else {
            // System.out.println("exit: " + car.getCardId() + " direction " + car.getDirectionInt());
            System.out.println("exit: " + car.getCardId() + ", " + car.getCurSegment());
        }

        inLock.unlock();

    }


}

class Car implements Runnable{

    private  long carId; // thread id
    private Direction direction; // Right or Left, enum
    private int directionInt; // 1 or -1
    private int curSegment; // the segment the car currently is at
    private int timePerSegment; // time need to move to one segment to the other
    private boolean isResidenceCar; // if it is a resident car
    private StreetMonitor monitor; // to hold the monitor objext

    public Car(Direction direction, int startSegment, int d, boolean isResidenceCar, StreetMonitor monitor) {
        this.carId = -1; // can only be initialized in run()
        this.direction = direction;
        if(direction == Direction.Right){
            this.directionInt = TrafficScheduler.RIGHT;
        } else{
            this.directionInt = TrafficScheduler.LEFT; 
        }
        this.curSegment = startSegment;
        this.timePerSegment = d;
        this.isResidenceCar = isResidenceCar;
        this.monitor = monitor;
        
    }

    public long getCardId(){
        return this.carId;
    }

    public Direction getDirection(){
        return this.direction;
    }

    public int getDirectionInt(){
        return this.directionInt;
    }
    public int getCurSegment(){
        return this.curSegment;
    }
    public int getTimePerSegment(){
        return this.timePerSegment;
    }
    public boolean isResidenceCar(){
        return this.isResidenceCar;
    }

    public void setCurSegment(int segment){
        this.curSegment = segment;
    }


    @Override
    public void run() {
        this.carId = Thread.currentThread().getId();
        // print out generated car here because only then will the car id be initialized
        System.out.println("car:" + this.getCardId() + "," + this.getCurSegment() + "," + this.getDirectionInt());
        monitor.enter(this); // the function runs until car is entered
        monitor.move(this); // move until the car leave

    }

}

class CarGenerator implements Runnable{
    private static int numSegments;
    private static int timePerNewCar;
    private static int timePerSegment;
    private static StreetMonitor monitor;

    public CarGenerator(int n, int s, int d, StreetMonitor monitor){
        CarGenerator.timePerNewCar = s;
        CarGenerator.numSegments = n;
        CarGenerator.timePerSegment = d;
        CarGenerator.monitor = monitor;
    }

    // generate new car base on probability
    public static Car generateNewCar(){
        Random random = new Random();

        int type = random.nextInt(0,101); // 101 exclusive

        if (type < 45) {
            return new Car(Direction.Right, 0, timePerSegment, false, monitor);
        } else if(type < 90) {
            return new Car(Direction.Left, CarGenerator.numSegments - 1, timePerSegment, false, monitor);
        } else {
            boolean dir = random.nextBoolean();
            int seg = random.nextInt(1,CarGenerator.numSegments-1); // between 1 and second to last segment
            if (dir == true) {
                return new Car(Direction.Right, seg, timePerSegment, true, monitor);
            } else {
                return new Car(Direction.Left, seg, timePerSegment, true, monitor);
            }
        }
    }

    @Override
    public void run() {
        Random random = new Random();
        while(true) {
            try {
                // sleep for s +- 20ms
                Thread.sleep(random.nextInt(CarGenerator.timePerNewCar - 20, timePerNewCar + 20 + 1));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            Car nextCar = generateNewCar();
            new Thread(nextCar).start();  
        }
    }

    
}
