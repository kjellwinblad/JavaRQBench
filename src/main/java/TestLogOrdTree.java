

public class TestLogOrdTree{ 
    static protected final class Elem implements Comparable<Elem>{
        private int key;


        public Elem(int key){
            this.key = key;
        }

        public int compareTo(Elem o){
            return key - o.key;
        }
        public String toString(){
            return new Integer(key).toString();
        }
        public int hashCode() {
            return key * 0x9e3775cd;
        }

        public int getKey(){
            return key;
        }

        public void setKey(int key){
            this.key = key;
        }
        
    }

    public static void main(String [] args){
        System.err.println("BEFORE CONSTRUCT");
        java.util.Map<Elem, Elem> benchMap = new trees.logicalordering.LogicalOrderingAVL<Elem,Elem>(new Elem(Integer.MIN_VALUE/2),
                                                                                                     new Elem(Integer.MAX_VALUE/2));
        System.err.println("AFTER CONSTRUCT");
        benchMap.put(new Elem(1),new Elem(1));
        System.err.println("AFTER PUT");
        
    }
}
