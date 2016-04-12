package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemImInitializationParams;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.sem.LargeSemSimulator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * Created by jogarrio on 4/11/16.
 */
public class jmTest {
    private final static RandomUtil ru = RandomUtil.getInstance();

    public static void main(String[] args){

        ru.setSeed(1460481944902L);

        //long seed = ru.getSeed();
        int numNodes = 1000;
        int numEdges = 1000;
        int numLatents = 50;

        List<Node> vars = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            vars.add(new ContinuousVariable("X" + (i + 1)));
        }

        Graph dag = GraphUtils.randomGraphRandomForwardEdges(vars, numLatents, numEdges, numNodes, numNodes, numNodes, false);

        DataSet data;
        int sampleSize = 200;

        /*
        SemPm pm = new SemPm(dag)

        SemImInitializationParams params = new SemImInitializationParams();
        params.setCoefRange(.2, 1.5);
        params.setCoefSymmetric(true);
        params.setVarRange(1, 3);
        params.setCoefSymmetric(true);

        SemIm im = new SemIm(pm, params);
        data = im.simulateData(sampleSize, false);*/

        ru.setSeed(1456264274620L);

        LargeSemSimulator sem = new LargeSemSimulator(dag);
        sem.setCoefRange(.2, 1.5);
        sem.setVarRange(1, 3);

        ru.setSeed(1456264274620L);
        data = sem.simulateDataAcyclic(sampleSize);
        data = DataUtils.restrictToMeasured(data);

        CovarianceMatrix cov = new CovarianceMatrix(data);
        SemBicScore score = new SemBicScore(cov);
        score.setPenaltyDiscount(4);


        edu.cmu.tetrad.search.Fgs fgs = new edu.cmu.tetrad.search.Fgs(data);
        fgs.setDepth(-1);
        fgs.setFaithfulnessAssumed(false);
        Graph outgraph = fgs.search();

        IndTestFisherZ test = new IndTestFisherZ(cov, .01);
        GFci gfci = new GFci(test);
        gfci.setDepth(3);

        Graph outpag = gfci.search();

        //Graph pat = SearchGraphUtils.patternForDag(dag);
        DagToPag1 dtp = new DagToPag1(dag);
        Graph pag = (new jmTest.DagToPag1(dag)).convert();

        pag = GraphUtils.replaceNodes(pag, dag.getNodes());
        SearchGraphUtils.graphComparison(outgraph, pag, System.out);
        SearchGraphUtils.graphComparison(outpag, pag, System.out);



        //System.out.println(seed);
    }

    public static class DagToPag1 {

        private static Graph dag;
        private static IndTestDSep dsep;

        /*
         * The background knowledge.
         */
        private IKnowledge knowledge = new Knowledge2();

        /**
         * The variables to search over (optional)
         */
        private List<Node> variables = new ArrayList<Node>();

        /**
         * Glag for complete rule set, true if should use complete rule set, false otherwise.
         */
        private boolean completeRuleSetUsed = false;

        /**
         * The logger to use.
         */
        private TetradLogger logger = TetradLogger.getInstance();

        /**
         * True iff verbose output should be printed.
         */
        private boolean verbose = false;
        private int maxPathLength = -1;
        private Graph truePag;

        private GraphAncestors ancestors;

        //============================CONSTRUCTORS============================//

        /**
         * Constructs a new FCI search for the given independence test and background knowledge.
         */
        public DagToPag1(Graph dag) {
            this.dag = dag;
            this.variables.addAll(dag.getNodes());

            this.dsep = new IndTestDSep(dag);

            this.ancestors = new GraphAncestors(dag);

        }


        //========================PUBLIC METHODS==========================//

        public Graph convert() {
            logger.log("info", "Starting DAG to PAG.");

            System.out.println("DAG to PAG: Starting adjacency search");

            Graph graph = calcAdjacencyGraph();

            System.out.println("DAG to PAG: Starting collider orientation");

            orientUnshieldedColliders(graph, dag);

            System.out.println("DAG to PAG: Starting final orientation");

            final FciOrient fciOrient = new FciOrient(new DagSepsets(dag));
            fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
            fciOrient.setChangeFlag(false);
            fciOrient.setMaxPathLength(maxPathLength);
            fciOrient.doFinalOrientation(graph);

            System.out.println("Finishing final orientation");

            return graph;
        }

        private Graph calcAdjacencyGraph() {
            List<Node> allNodes = dag.getNodes();
            List<Node> measured = new ArrayList<Node>();

            for (Node node : allNodes) {
                if (node.getNodeType() == NodeType.MEASURED) {
                    measured.add(node);
                }
            }

            Graph graph = new EdgeListGraphSingleConnections(measured);

            for (int i = 0; i < measured.size(); i++) {
                for (int j = i + 1; j < measured.size(); j++) {
                    Node n1 = measured.get(i);
                    Node n2 = measured.get(j);

                    final List<Node> inducingPath = getInducingPath1(n1, n2, dag);

                    boolean exists = inducingPath != null;

                    if (exists) {
                        graph.addEdge(Edges.nondirectedEdge(n1, n2));
                    }
                }
            }

            return graph;
        }

        private void orientUnshieldedColliders(Graph graph, Graph dag) {
            graph.reorientAllWith(Endpoint.CIRCLE);

            List<Node> allNodes = dag.getNodes();
            List<Node> measured = new ArrayList<Node>();

            for (Node node : allNodes) {
                if (node.getNodeType() == NodeType.MEASURED) {
                    measured.add(node);
                }
            }

            for (Node b : measured) {
                List<Node> adjb = graph.getAdjacentNodes(b);

                if (adjb.size() < 2) continue;

                for (int i = 0; i < adjb.size(); i++) {
                    for (int j = i + 1; j < adjb.size(); j++) {
                        Node a = adjb.get(i);
                        Node c = adjb.get(j);

                        if (graph.isDefCollider(a, b, c)) {
                            continue;
                        }

                        if (graph.isAdjacentTo(a, c)) {
                            continue;
                        }

                        boolean found = foundCollider(dag, a, b, c);

                        if (found) {
                            System.out.println("Orienting collider " + a + "*->" + b + "<-*" + c);
                            graph.setEndpoint(a, b, Endpoint.ARROW);
                            graph.setEndpoint(c, b, Endpoint.ARROW);
                        }
                    }
                }
            }
        }

        public boolean existsInducingPathInto1(Node x, Node y, Graph graph) {
            if (x.getNodeType() != NodeType.MEASURED) throw new IllegalArgumentException();
            if (y.getNodeType() != NodeType.MEASURED) throw new IllegalArgumentException();

            final LinkedList<Node> path = new LinkedList<Node>();
            path.add(x);

            for (Node b : graph.getAdjacentNodes(x)) {
                Edge edge = graph.getEdge(x, b);
                if (!edge.pointsTowards(x)) continue;

                if (existsInducingPathVisit1(graph, x, b, x, y, path)) {
                    return true;
                }
            }

            return false;
        }

        public boolean existsInducingPath1(Node x, Node y, Graph graph) {
            if (x.getNodeType() != NodeType.MEASURED) throw new IllegalArgumentException();
            if (y.getNodeType() != NodeType.MEASURED) throw new IllegalArgumentException();


            final LinkedList<Node> path = new LinkedList<Node>();
            path.add(x);

            for (Node b : graph.getAdjacentNodes(x)) {
                if (existsInducingPathVisit1(graph, x, b, x, y, path)) {
                    return true;
                }
            }

            return false;
        }

        private boolean existsInducingPathVisit1(Graph graph, Node a, Node b, Node x, Node y,
                                                 LinkedList<Node> path) {
            if (b == y) {
                path.addLast(b);
                return true;
            }

            if (path.contains(b)) {
                return false;
            }

            path.addLast(b);

            for (Node c : graph.getAdjacentNodes(b)) {
                if (c == a) continue;

                if (b.getNodeType() == NodeType.MEASURED) {
                    if (!graph.isDefCollider(a, b, c)) continue;

                }

                if (graph.isDefCollider(a, b, c)) {

                    if (!(ancestors.isAncestorOf(b, x) || ancestors.isAncestorOf(b, y))) {
                        continue;
                    }
                }

                if (existsInducingPathVisit1(graph, b, c, x, y, path)) {
                    return true;
                }
            }

            path.removeLast();
            return false;
        }

        public List<Node> getInducingPath1(Node x, Node y, Graph graph) {
            if (x.getNodeType() != NodeType.MEASURED) throw new IllegalArgumentException();
            if (y.getNodeType() != NodeType.MEASURED) throw new IllegalArgumentException();

            final LinkedList<Node> path = new LinkedList<Node>();
            path.add(x);

            for (Node b : graph.getAdjacentNodes(x)) {
                if (existsInducingPathVisit1(graph, x, b, x, y, path)) {
                    return path;
                }
            }

            return null;
        }

        private boolean foundCollider(Graph dag, Node a, Node b, Node c) {
            boolean ipba = existsInducingPathInto1(b, a, dag);
            boolean ipbc = existsInducingPathInto1(b, c, dag);

            if (!(ipba && ipbc)) {
                printTrueDefCollider(a, b, c, false);
                return false;
            }

            printTrueDefCollider(a, b, c, true);

            return true;
        }

        private void printTrueDefCollider(Node a, Node b, Node c, boolean found) {
            if (truePag != null) {
                final boolean defCollider = truePag.isDefCollider(a, b, c);

                if (!found && defCollider) {
                    System.out.println("FOUND COLLIDER FCI");
                } else if (found && !defCollider) {
                    System.out.println("DIDN'T FIND COLLIDER FCI");
                }
            }
        }




        public IKnowledge getKnowledge() {
            return knowledge;
        }

        public void setKnowledge(IKnowledge knowledge) {
            if (knowledge == null) {
                throw new NullPointerException();
            }

            this.knowledge = knowledge;
        }

        /**
         * @return true if Zhang's complete rule set should be used, false if only R1-R4 (the rule set of the original FCI)
         * should be used. False by default.
         */
        public boolean isCompleteRuleSetUsed() {
            return completeRuleSetUsed;
        }

        /**
         * @param completeRuleSetUsed set to true if Zhang's complete rule set should be used, false if only R1-R4 (the rule
         *                            set of the original FCI) should be used. False by default.
         */
        public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
            this.completeRuleSetUsed = completeRuleSetUsed;
        }

        /**
         * True iff verbose output should be printed.
         */
        public boolean isVerbose() {
            return verbose;
        }

        public void setVerbose(boolean verbose) {
            this.verbose = verbose;
        }

        public int getMaxPathLength() {
            return maxPathLength;
        }

        public void setMaxPathLength(int maxPathLength) {
            this.maxPathLength = maxPathLength;
        }

        public Graph getTruePag() {
            return truePag;
        }

        public void setTruePag(Graph truePag) {
            this.truePag = truePag;
        }
    }

    public static class GraphAncestors {
        private Map<String, HashSet<String>> ancestors;

        public GraphAncestors(Graph graph) {
            ancestors = new HashMap<>((int) Math.ceil(graph.getNumNodes() / .75));

            for (Node node : graph.getNodes()) {
                ancestors.put(node.getName(), new HashSet<String>());

            }

            findAncestors(graph);

        }

        public boolean isAncestorOf(Node node1, Node node2) {
            return ancestors.get(node2.getName()).contains(node1.getName());
        }

        private void findAncestors(Graph graph) {
            //System.out.println("Finding ancestors");
            //int index = 1;
            for (Node node : graph.getNodes()) {
                //System.out.println("Ancestor " + index++ + " of " + graph.getNumNodes());
                HashSet<Node> checked = new HashSet<>();
                LinkedList<Node> queue = new LinkedList<>();
                queue.add(node);
                while (!queue.isEmpty()) {
                    Node current = queue.remove();
                    if (checked.contains(current)) continue;
                    checked.add(current);
                    ancestors.get(current.getName()).add(node.getName());

                    for (Node child : graph.getChildren(current)) {
                        queue.add(child);
                    }
                }

            }
        }
    }
}
