package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.LargeSemSimulator;
import edu.cmu.tetrad.util.ChoiceGenerator;

import java.io.PrintStream;
import java.util.*;

/**
 * Created by jogarrio on 2/19/16.
 */
public class JmoMeasurementUtils {

    public static class GraphNonNonAncestors {
        private Map<String, HashSet<String>> nonNonAncestors;

        public GraphNonNonAncestors(Graph graph){
            nonNonAncestors = new HashMap<>((int)Math.ceil(graph.getNumNodes()/.75));

            for(Node node : graph.getNodes()){
                nonNonAncestors.put(node.getName(), new HashSet<String>());

            }
            findNonNonAncestors(graph);

        }

        public boolean isNonNonAncestorOf(Node node1, Node node2){
            return nonNonAncestors.get(node2.getName()).contains(node1.getName());
        }

        private void findNonNonAncestors(Graph graph) {
            //System.out.println("Finding nonNonAncestors");
            //int index = 1;
            for(Node node: graph.getNodes()){
                //System.out.println("Ancestor " + index++ + " of " + graph.getNumNodes());
                HashSet<Node> checked = new HashSet<>();
                LinkedList<Node> queue = new LinkedList<>();
                queue.add(node);
                while(!queue.isEmpty()){
                    Node current = queue.remove();
                    if(checked.contains(current)) continue;
                    checked.add(current);

                    if(current != node)
                        nonNonAncestors.get(current.getName()).add(node.getName());

                    for(Edge edge: graph.getEdges(current)){
                        Endpoint proximal = edge.getProximalEndpoint(current);
                        Endpoint distal = edge.getDistalEndpoint(current);

                        if(!(proximal == Endpoint.ARROW && (distal != Endpoint.ARROW)))
                            continue;

                        else
                            queue.add(edge.getDistalNode(current));
                    }
                }

            }
        }
    }


    public static class GraphAncestors {
        private Map<String, HashSet<String>> ancestors;

        public GraphAncestors(Graph graph){
            ancestors = new HashMap<>((int)Math.ceil(graph.getNumNodes()/.75));

            for(Node node : graph.getNodes()){
                ancestors.put(node.getName(), new HashSet<String>());

            }

            findAncestors(graph);

        }

        public boolean isAncestorOf(Node node1, Node node2){
            return ancestors.get(node2.getName()).contains(node1.getName());
        }

        private void findAncestors(Graph graph) {
            //System.out.println("Finding ancestors");
            //int index = 1;
            for(Node node: graph.getNodes()){
                //System.out.println("Ancestor " + index++ + " of " + graph.getNumNodes());
                HashSet<Node> checked = new HashSet<>();
                LinkedList<Node> queue = new LinkedList<>();
                queue.add(node);
                while(!queue.isEmpty()){
                    Node current = queue.remove();
                    if(checked.contains(current)) continue;
                    checked.add(current);
                    ancestors.get(current.getName()).add(node.getName());

                    for(Node child : graph.getChildren(current)){
                        queue.add(child);
                    }
                }

            }
        }
    }

    static class TotalEffect {

        private HashMap<Node, Double> effects;

        class Effect {
            Node node;
            Edge edge;
            Double effect;
            public Effect(Node node, Edge edge, double effect){
                this.node = node;
                this.edge = edge;
                this.effect = effect;
            }
        }


        public double getEffect(Node y){
            if(!effects.containsKey(y))
                return 0;
            else
                return effects.get(y);
        }

        public TotalEffect(Node node,Graph dag, GraphAncestors ancestors, LargeSemSimulator sem){
            this.effects = new HashMap<>();
            LinkedList<Effect> stack = new LinkedList<Effect>();

            for(Node child: dag.getChildren(node)){
                Edge edge = dag.getEdge(node, child);
                stack.push(new Effect(child, edge, sem.getCoef(edge)));
            }

            while(!stack.isEmpty()){
                Effect e = stack.pop();
                Edge edge = e.edge;
                Node descendant = e.node;
                double pathEffect = e.effect*sem.getCoef(edge);

                if(effects.containsKey(descendant))
                    effects.put(descendant, effects.get(descendant)+pathEffect);
                else
                    effects.put(descendant, pathEffect);

                for(Node child: dag.getChildren(descendant)){
                    Edge newEdge = dag.getEdge(descendant,child);
                    stack.push(new Effect(child, newEdge, pathEffect));
                }
            }
        }

    }

    private static class AncestorScores{
        int ancestorTruePositive = 0;
        int ancestorFalsePositive = 0;
        int nonAncestorTruePositive = 0;
        int nonAncestorFalsePositive = 0;
        int totalTrueAncestor = 0;
        int totalTrueNonAncestor = 0;
        double difference = 0;

        private AncestorScores(Graph dag, Graph estPag, LargeSemSimulator sem) {


            GraphAncestors trueAncestors = new GraphAncestors(dag);
            GraphAncestors estAncestors = new GraphAncestors(estPag);
            GraphNonNonAncestors estNonNonAncestors = new GraphNonNonAncestors(estPag);

            for(Node x : estPag.getNodes()){

                TotalEffect totalEffect = new TotalEffect(x, dag, trueAncestors, sem);
                for (Node y : estPag.getNodes()){
                    if (x == y) continue;

                    if(trueAncestors.isAncestorOf(x,y))
                        totalTrueAncestor++;
                    else
                        totalTrueNonAncestor++;

                    if(estAncestors.isAncestorOf(x, y)) {
                        if (trueAncestors.isAncestorOf(x,y))
                            ancestorTruePositive++;
                        else
                            ancestorFalsePositive++;
                    }

                    else if(!estNonNonAncestors.isNonNonAncestorOf(x,y)){
                        if(trueAncestors.isAncestorOf(x,y)){
                            nonAncestorFalsePositive++;
                            difference += totalEffect.getEffect(y);
                        }
                        else
                            nonAncestorTruePositive++;
                    }
                }
            }
        }

        public int getAncestorTruePositive() {
            return ancestorTruePositive;
        }

        public int getAncestorFalsePositive() {
            return ancestorFalsePositive;
        }

        public int getNonAncestorTruePositive() {
            return nonAncestorTruePositive;
        }

        public int getNonAncestorFalsePositive() {
            return nonAncestorFalsePositive;
        }

        public int getTotalTrueAncestor() {
            return totalTrueAncestor;
        }

        public int getTotalTrueNonAncestor() {
            return totalTrueNonAncestor;
        }

        public double getDifference() {
            return difference;
        }
    }

    private static int[][] edgeClassificationCounts(Graph leftGraph, Graph topGraph){
        topGraph = GraphUtils.replaceNodes(topGraph, leftGraph.getNodes());

        int[][] counts = new int[8][6];

        for (Edge est : topGraph.getEdges()) {
            Node x = est.getNode1();
            Node y = est.getNode2();

            Edge left = leftGraph.getEdge(x, y);

            Edge top = topGraph.getEdge(x, y);

            int m = getTypeLeft(left, top);
            int n = getTypeTop(top);

            counts[m][n]++;
        }

        for (Edge edgeLeft : leftGraph.getEdges()) {
            final Edge edgeTop = topGraph.getEdge(edgeLeft.getNode1(), edgeLeft.getNode2());
            if (edgeTop == null) {
                int m = getTypeLeft(edgeLeft, edgeLeft);
                counts[m][5]++;
            }
        }

        return counts;
    }

    private static int[][] tripleClassificationCounts(Graph trueGraph, Graph estGraph){
        int[][] counts = new int[3][6];

        List<Node> nodes = trueGraph.getNodes();

        for (Node b : nodes) {
            List<Node> adjacentNodes = trueGraph.getAdjacentNodes(b);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node a = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                int row = getTrueTripleType(trueGraph, a, b, c);
                int col = getEstTripleType(estGraph, a,b,c);

                counts[row][col]++;
            }
        }

        return counts;
    }

    private static int getEstTripleType(Graph estGraph, Node a, Node b, Node c) {

        if(!(estGraph.isAdjacentTo(a,b) && estGraph.isAdjacentTo(b,c))) //Adjacency error
            return 5;

        if (estGraph.isDefCollider(a,b,c)){ // a*->b<-*c
            if (!estGraph.isAmbiguousTriple(a,b,c)) // unmarked
                return 0;
            else
                return 2;
        }

        //non-colliders

        if (estGraph.isAdjacentTo(a,c)) { // shielded
            Edge edge1 = estGraph.getEdge(a,b);
            Edge edge2 = estGraph.getEdge(c,b);

            if(!(edge1.getProximalEndpoint(b) == Endpoint.TAIL || edge2.getProximalEndpoint(b) == Endpoint.TAIL))
                return 4;       //ambiguous

        }
        if (estGraph.isAmbiguousTriple(a,b,c)) //marked non-collider
            return 3;

        return 1; // non-collider
    }

    private static int getTrueTripleType(Graph trueGraph, Node a, Node b, Node c) {

        if (trueGraph.isDefCollider(a,b,c)) // a*->b<-*c
            return 0;
        else if (!trueGraph.isAdjacentTo(a,c)) // unshielded non-collider
            return 1;
        else                                    //shielded case, ambiguous
            return 2;

    }

    private static int getTypeTop(Edge edgeTop) {
        if (edgeTop == null) {
            return 5;
        }

        Endpoint e1 = edgeTop.getEndpoint1();
        Endpoint e2 = edgeTop.getEndpoint2();

        if (e1 == Endpoint.TAIL && e2 == Endpoint.TAIL) {
            return 0;
        }

        if (e1 == Endpoint.CIRCLE && e2 == Endpoint.CIRCLE) {
            return 1;
        }

        if (e1 == Endpoint.CIRCLE && e2 == Endpoint.ARROW) {
            return 2;
        }

        if (e1 == Endpoint.TAIL && e2 == Endpoint.ARROW) {
            return 3;
        }

        if (e1 == Endpoint.ARROW && e2 == Endpoint.ARROW) {
            return 4;
        }

        throw new IllegalArgumentException("Unsupported edgeTop type : " + e1 + " " + e2);
    }

    private static int getTypeLeft(Edge edgeLeft, Edge edgeTop) {
        if (edgeLeft == null) {
            return 7;
        }

        Endpoint e1 = edgeLeft.getEndpoint1();
        Endpoint e2 = edgeLeft.getEndpoint2();

        if (e1 == Endpoint.TAIL && e2 == Endpoint.TAIL) {
            return 0;
        }

        if (e1 == Endpoint.CIRCLE && e2 == Endpoint.CIRCLE) {
            return 1;
        }

        if (e1 == Endpoint.CIRCLE && e2 == Endpoint.ARROW && edgeTop.equals(edgeLeft.reverse())) {
            return 3;
        }

        if (e1 == Endpoint.CIRCLE && e2 == Endpoint.ARROW) {
            return 2;
        }

        if (e1 == Endpoint.TAIL && e2 == Endpoint.ARROW && edgeTop.equals(edgeLeft.reverse())) {
            return 5;
        }

        if (e1 == Endpoint.TAIL && e2 == Endpoint.ARROW) {
            return 4;
        }

        if (e1 == Endpoint.ARROW && e2 == Endpoint.ARROW) {
            return 6;
        }

        throw new IllegalArgumentException("Unsupported edge type : " + e1 + " " + e2);
    }

}
