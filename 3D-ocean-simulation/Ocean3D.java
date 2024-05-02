import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

// creature as 2d array [[x,y,z],[x,y,z][x,y,z]]
// see as 3d array [[[lock1,lock2],[lock3,lock4]], [[lock5,lock6],[lock7,lock8]]]
// Each cell is a lock 
// for each cell in new position, check if locked or if the lock holder is self, otherwise give up?
//
public class Ocean3D {



    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Needs to have exactly 2 arguments");
            System.exit(1);
        }
        int k = 0;
        int n = 0;

        try{
            k = Integer.parseInt(args[0]);
            n = Integer.parseInt(args[1]);
        } catch (Exception e){
            System.err.println("Something wrong with the arguments");
            System.exit(1);
        }

        int mapSize = 5 * k;
        ReentrantLock[][][] sea = new ReentrantLock[mapSize][mapSize][mapSize];
        Integer[][][] seaOccupied = new Integer[mapSize][mapSize][mapSize];
        for (int i = 0; i < mapSize; i++) {
            for (int j = 0; j < mapSize; j++) {
                for (int m = 0; m < mapSize; m++){
                    sea[i][j][m] = new ReentrantLock();
                    seaOccupied[i][j][m] = -1;
                }
            }
        }

        int[][][] creatureList = new int[4][][];
        int[][] creature1 = {{0, 0, 0},{0, 0, 1},{0, 0, 2}};
        int[][] creature2 = {{1, 1, 0},{1, 0, 1},{0, 1, 1},{1, 1, 1},{2, 1, 1},{1, 2, 1},{1, 1, 2}};
        int[][] creature3 = {{0, 0, 0},{1, 0, 0},{0, 0, 1},{0, 0, 2},{0, 1, 2}};
        int[][] creature4 = {{0, 0, 0},{2, 0, 0},{0, 0, 1},{1, 0, 1},{2, 0, 1},{1, 0, 2}};
        creatureList[0] = creature1;
        creatureList[1] = creature2;
        creatureList[2] = creature3;
        creatureList[3] = creature4;

        AtomicBoolean flag = new AtomicBoolean(false);
        AtomicInteger numPreparing = new AtomicInteger(k);
        
        ArrayList<Thread> threads = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            threads.add(new Thread(new SeaCreature(i, (i%4) + 1, sea, seaOccupied, creatureList[i % 4], flag,numPreparing)));
        }

        threads.forEach(Thread::start);

        while(numPreparing.get() != 0) {

        }
        
        System.err.println("Starting simulation");

        try {
            Thread.sleep(n*1000); //convert to second
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        System.out.println("about to join");
        flag.set(true);
        for(Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.exit(0);


    }



}

class SeaCreature implements Runnable{
    AtomicBoolean terminateFlag;
    AtomicInteger numPreparing;

    Integer creatureId;
    Integer type;

    ReentrantLock[][][] sea;
    Integer[][][] seaOccupied;
    int seaSize;

    int[][] createShape;

    int[][] creatureCurCoord;
    int[][] creatureNextCoord;



    public SeaCreature(Integer id, Integer type,ReentrantLock[][][] sea, Integer[][][] seaOccupied, int[][] shape, AtomicBoolean startFlag, AtomicInteger numPreparing) {
        this.creatureId = id;
        this.type = type;
        
        this.sea = sea;
        this.createShape = shape;
        this.seaOccupied = seaOccupied;
        this.seaSize = sea.length;

        this.terminateFlag = startFlag;
        this.numPreparing = numPreparing;

    }

    // if return for example 3,20,4, then the reference coordinate would be at (3,20,4), the second on would be the coordinate plus the createrureshape[1]
    private int[] generateNewMove(){
        Random random = new Random();
        int[] move = new int[3];
        for (int i = 0; i < move.length; i++) {
            move[i] = random.nextInt(-1,2);
        }

        return move;
    }

    private int[][] findNewCreatureCoord(int[] locCoord) {
        int[][] nextCoord = new int[this.createShape.length][3];
        for (int i = 0; i < this.creatureCurCoord.length; i++) {
            nextCoord[i][0] = this.creatureCurCoord[i][0] + locCoord[0];
            nextCoord[i][1] = this.creatureCurCoord[i][1] + locCoord[1];
            nextCoord[i][2] = this.creatureCurCoord[i][2] + locCoord[2];
        }

        return nextCoord;


    }

    private boolean isOutOfBound(int[][] nextCoord) {
        for (int i = 0; i < this.creatureCurCoord.length; i++) {
            int x = nextCoord[i][0];
            int y = nextCoord[i][1];
            int z = nextCoord[i][2];
            if (x < 0 || x >= seaSize || y < 0 || y >= seaSize || z < 0 || z >= seaSize) {
                return true;
            }
        }

        return false;
    }

    private int[][] combineTwoCoordinates(int[][] curCoord, int[][] nextCoord){
        int[][] combinedCoord = new int[this.createShape.length * 2][];
        int j = 0;
        for (int i = 0; i < curCoord.length; i ++){
            combinedCoord[j] = curCoord[i];
            j++;
        }
        for (int i = 0; i < nextCoord.length; i ++){
            combinedCoord[j] = nextCoord[i];
            j++;
        }

        return combinedCoord;
        
    }

    private int[][] acquireLocksOfSea(int[][] allCellsToTake) {
        Arrays.sort(allCellsToTake, (arr1,arr2) -> {
            for (int i = 0; i < 3; i++) {
                if (arr1[i] > arr2[i]){
                    return 1; // arr1 is larger, should go on the right
                } else if (arr1[i] < arr2[2]) {
                    return -1;
                }
            } 
            return 0;
        });

        for (int i = 0; i < allCellsToTake.length; i++) {
            int x = allCellsToTake[i][0];
            int y = allCellsToTake[i][1];
            int z = allCellsToTake[i][2];
            // System.out.println(creatureId + " lockng " + x + " " + y + " " + z + " " );
            sea[x][y][z].lock();
        }

        // all cells locked
        return allCellsToTake; //array.sort already modify the argument itself but return it just to be safe

    }

    private void releaseLocksOfSea(int[][] allCellsToTake) {

        for (int i = 0; i < allCellsToTake.length; i++) {
            int x = allCellsToTake[i][0];
            int y = allCellsToTake[i][1];
            int z = allCellsToTake[i][2];
            // System.out.println(creatureId + " unlockng " + x + " " + y + " " + z + " " );
            sea[x][y][z].unlock();
        }

    }

    private boolean isOccupiedBySomeoneElse(int[][] cellsToTake){
        for (int i = 0; i < cellsToTake.length; i++) {
            int x = cellsToTake[i][0];
            int y = cellsToTake[i][1];
            int z = cellsToTake[i][2];
            if (seaOccupied[x][y][z] != -1 & seaOccupied[x][y][z] != this.creatureId) {
                return true;
            }
        }

        return false;

    }

    private void freePreviousPosition(int[][] previousCells) {
        for (int i = 0; i < previousCells.length; i++) {
            int x = previousCells[i][0];
            int y = previousCells[i][1];
            int z = previousCells[i][2];
            seaOccupied[x][y][z] = -1;
        }
    }

    private void occupyNewPosition(int[][] newCells) {
        for (int i = 0; i < newCells.length; i++) {
            int x = newCells[i][0];
            int y = newCells[i][1];
            int z = newCells[i][2];
            seaOccupied[x][y][z] = this.creatureId;
        }
    }

    private String stringOfCoordinate(int[][] coordinate) {
        String s = "";
        for (int i = 0; i < coordinate.length; i++) {
            s += ("<" + coordinate[i][0] + " " + coordinate[i][1] + " " + coordinate[i][2] + " > ");
        }
        return s;

    }


    @Override
    public void run() {

        Random random = new Random();
        boolean readyFlag = false;
        System.out.println("Finding spawn location for Creature id:" + creatureId + " type " + type);
        while (!readyFlag) {
            int[] move = new int[3];
            // find random spot in the sea (not +1 -1 but actual coordinate)
            for (int i = 0; i < move.length; i++) {
                move[i] = random.nextInt(0,this.sea.length); //this.sea.length =  k
            }
            
            // initialized current coordinate to placeholder 
            this.creatureCurCoord = this.createShape;
            // find absolute coordinate of the spawning position
            int[][] nextCoord = this.findNewCreatureCoord(move);

            if(!isOutOfBound(nextCoord)){
                nextCoord = this.acquireLocksOfSea(nextCoord);
                // System.out.println("preparation lock acquired");
                if(!isOccupiedBySomeoneElse(nextCoord)){
                    occupyNewPosition(nextCoord);
                    readyFlag = true;
                    this.creatureCurCoord = nextCoord;
                } 

                // System.out.println("preparation lock released");
                releaseLocksOfSea(nextCoord);
    
            }

            //loop continues any of the if statement fails

        }

        System.out.println("Ready for Creature id:" + creatureId + " type " + type);

        numPreparing.decrementAndGet();

        // wait until all creatures are spawned
        while (numPreparing.get() != 0) {

        }

        // while the parent thread hasn't say stop (work for n seconds)
        while (!this.terminateFlag.get()) {
            try {
                Thread.sleep(random.nextInt(10,51));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // get next relative position
            int[] nextMove = this.generateNewMove();
            // System.err.println(nextMove[0] + " " + nextMove[1] + " " + nextMove[2]);
            int[][] currentCoordRecord = this.creatureCurCoord;
            int[][] nextCoord = this.findNewCreatureCoord(nextMove);

            // if next coordinate not out of bound 
            if(!isOutOfBound(nextCoord)){
                int[][] allCellsToTake = combineTwoCoordinates(this.creatureCurCoord, nextCoord);
                allCellsToTake = this.acquireLocksOfSea(allCellsToTake);

                if(isOccupiedBySomeoneElse(nextCoord)){
                    nextCoord = this.creatureCurCoord;
                } else {
                    freePreviousPosition(this.creatureCurCoord);
                    occupyNewPosition(nextCoord);
                    this.creatureCurCoord = nextCoord;
                }

                //release all the locks regardless of result
                releaseLocksOfSea(allCellsToTake);

                
            } else {
                nextCoord = this.creatureCurCoord;
            }

            System.out.println("\nCreature Id: " + this.creatureId + " type: " + this.type + " previous coord: " + 
            stringOfCoordinate(currentCoordRecord) + " destination: "  + stringOfCoordinate(nextCoord));
        }
    }
} 