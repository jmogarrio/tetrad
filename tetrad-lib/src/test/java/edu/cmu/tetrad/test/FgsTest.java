package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.Fgs;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.search.SemBicScore;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemImInitializationParams;
import edu.cmu.tetrad.sem.SemPm;

/**
 * Created by jogarrio on 4/11/16.
 */
public class FgsTest {
    public static void main(String[] args){

        int numNodes = 100;
        int numEdges = 100;
        int numLatents = 0;

        Graph dag = GraphUtils.randomGraphRandomForwardEdges(numNodes,numLatents, numEdges, 30,15,15,false);

        DataSet data;
        int sampleSize = 200;

        SemPm pm = new SemPm(dag);

        SemImInitializationParams params = new SemImInitializationParams();
        params.setCoefRange(.2, 1.5);
        params.setCoefSymmetric(true);
        params.setVarRange(1, 3);
        params.setCoefSymmetric(true);

        SemIm im = new SemIm(pm, params);
        data = im.simulateData(sampleSize, false);

        CovarianceMatrix cov = new CovarianceMatrix(data);
        SemBicScore score = new SemBicScore(cov);
        score.setPenaltyDiscount(4);

        Fgs fgs = new Fgs(score);
        Graph outgraph = fgs.search();

        Graph pat = SearchGraphUtils.patternForDag(dag);
        SearchGraphUtils.graphComparison(outgraph, pat, System.out);

    }
}
