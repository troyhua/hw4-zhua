package edu.cmu.lti.f13.hw4.hw4_zhua.casconsumers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.collection.CasConsumer_ImplBase;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSList;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceProcessException;
import org.apache.uima.util.ProcessTrace;

import edu.cmu.lti.f13.hw4.hw4_zhua.typesystems.Document;
import edu.cmu.lti.f13.hw4.hw4_zhua.typesystems.Token;
import edu.cmu.lti.f13.hw4.hw4_zhua.utils.Utils;

public class RetrievalEvaluator extends CasConsumer_ImplBase {

  class Pair {
    public int index;

    public double score;

    public Pair(int index) {
      this.index = index;
    }
  }

  class QuerySet {
    public int queryIndex;

    public int queryID;

    public List<Pair> answerList;

    public QuerySet(int queryID) {
      answerList = new ArrayList<Pair>();
      this.queryID = queryID;
    }
  }

  /** query id number **/
  public ArrayList<Integer> qIdList;

  /** query and text relevant values **/
  public ArrayList<Integer> relList;

  public ArrayList<Hashtable<String, Integer>> dictList;

  public Hashtable<Integer, QuerySet> allQueries;

  public List<String> contentList;

  public void initialize() throws ResourceInitializationException {

    qIdList = new ArrayList<Integer>();

    relList = new ArrayList<Integer>();

    dictList = new ArrayList<Hashtable<String, Integer>>();

    allQueries = new Hashtable<Integer, QuerySet>();

    contentList = new ArrayList<String>();
  }

  /**
   * 1. construct the global word dictionary 2. keep the word frequency for each sentence
   */
  @Override
  public void processCas(CAS aCas) throws ResourceProcessException {

    JCas jcas;
    try {
      jcas = aCas.getJCas();
    } catch (CASException e) {
      throw new ResourceProcessException(e);
    }

    FSIterator it = jcas.getAnnotationIndex(Document.type).iterator();

    if (it.hasNext()) {
      Document doc = (Document) it.next();

      contentList.add(doc.getText());

      FSList fsTokenList = doc.getTokenList();
      ArrayList<Token> tokenList = Utils.fromFSListToCollection(fsTokenList, Token.class);

      int qID = doc.getQueryID();

      qIdList.add(qID);
      relList.add(doc.getRelevanceValue());

      Hashtable<String, Integer> table = new Hashtable<String, Integer>();

      for (Token token : tokenList) {
        table.put(token.getText(), token.getFrequency());
      }
      dictList.add(table);
    }
  }

  /**
   * 1. Compute Cosine Similarity and rank the retrieved sentences 2. Compute the MRR metric
   */
  @Override
  public void collectionProcessComplete(ProcessTrace arg0) throws ResourceProcessException,
          IOException {

    super.collectionProcessComplete(arg0);

    for (int i = 0; i < qIdList.size(); i++) {

      QuerySet qSet = null;
      if (allQueries.containsKey(qIdList.get(i))) {
        qSet = allQueries.get(qIdList.get(i));
      } else {
        qSet = new QuerySet(qIdList.get(i));
        allQueries.put(qIdList.get(i), qSet);
      }

      if (relList.get(i) == 99) {
        qSet.queryIndex = i;
      } else {
        qSet.answerList.add(new Pair(i));
      }
    }

    // The sum of RR in all queries
    double rrSum = 0.0;

    for (QuerySet qSet : allQueries.values()) {
      for (Pair pair : qSet.answerList) {
        // Compute cosine score to each possible answer(document)
//        pair.score = computeDiceScore(dictList.get(qSet.queryIndex), dictList.get(pair.index));
        pair.score = computeScore(dictList.get(qSet.queryIndex), dictList.get(pair.index));
//        pair.score = computeJaccardScore(dictList.get(qSet.queryIndex), dictList.get(pair.index));
      }
      // sort the index-score pair based on the score
      Collections.sort(qSet.answerList, new Comparator<Pair>() {
        @Override
        public int compare(Pair arg0, Pair arg1) {
          if (arg0.score > arg1.score)
            return -1;
          else
            return 1;
        }
      });
      // compute RR from the sorted pair list and add it to the sum of RR
      rrSum += computeRR(qSet.answerList);
    }

    // get the average RR
    double metric_mrr = rrSum / allQueries.size();
    System.out.println(" (MRR) Mean Reciprocal Rank ::" + metric_mrr);
  }

  private void printDebug(QuerySet qset) {
    System.out.println("**********");
    System.out.println("QueryIndex: " + qset.queryIndex);
    System.out.println("Query: " + contentList.get(qset.queryIndex));
    for (Pair pair : qset.answerList) {
      System.out.println(pair.score + " " + relList.get(pair.index) + " "
              + contentList.get(pair.index));
    }
  }

  private double computeRR(List<Pair> pairs) {
    for (int i = 0; i < pairs.size(); i++) {
      if (relList.get(pairs.get(i).index) == 1) {
        System.out.println("Score: " + pairs.get(i).score + "\trank=" + (i + 1) + "\trel=1\tqid="
                + qIdList.get(pairs.get(i).index) + "\t" + contentList.get(pairs.get(i).index));
        return 1.0 / (i + 1);
      }
    }
    return 0;
  }

  public double computeScore(Hashtable<String, Integer> queryTable,
          Hashtable<String, Integer> answerTable) {
    double sum = 0.0;
    for (String key1 : queryTable.keySet()) {
      if (answerTable.containsKey(key1))
        sum += queryTable.get(key1) * queryTable.get(key1);
    }
    double sum2 = 0.0;
    for (Integer value : answerTable.values())
      sum2 += value * value;
    double sum1 = 0.0;
    for (Integer value : queryTable.values())
      sum1 += value * value;
    sum1 = Math.sqrt(sum1);
    sum2 = Math.sqrt(sum2);
    if (sum2 == 0.0 || sum1 == 0.0)
      return 0;
    return sum / sum2 / sum1;
  }

  public double computeDiceScore(Hashtable<String, Integer> queryTable,
          Hashtable<String, Integer> answerTable) {
    int commonNum = 0;
    for (String key1 : queryTable.keySet()) {
      if (answerTable.containsKey(key1))
        commonNum++;
    }
    return 2.0 * commonNum / (queryTable.size() + answerTable.size());
  }
  
  
  public double computeJaccardScore(Hashtable<String, Integer> queryTable,
          Hashtable<String, Integer> answerTable) {
    int commonNum = 0;
    for (String key1 : queryTable.keySet()) {
      if (answerTable.containsKey(key1))
        commonNum++;
    }
    return 2.0 * commonNum / ( queryTable.size() + answerTable.size() - commonNum);
  }
}
