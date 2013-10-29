package edu.cmu.lti.f13.hw4.hw4_zhua.annotators;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import edu.cmu.lti.f13.hw4.hw4_zhua.VectorSpaceRetrieval;
import edu.cmu.lti.f13.hw4.hw4_zhua.typesystems.Document;
import edu.cmu.lti.f13.hw4.hw4_zhua.typesystems.Token;
import edu.cmu.lti.f13.hw4.hw4_zhua.utils.Utils;

import edu.stanford.nlp.process.Morphology;

public class DocumentVectorAnnotator extends JCasAnnotator_ImplBase {

	@Override
	public void process(JCas jcas) throws AnalysisEngineProcessException {

		FSIterator<Annotation> iter = jcas.getAnnotationIndex().iterator();
		if (iter.isValid()) {
			iter.moveToNext();
			Document doc = (Document) iter.get();
			createTermFreqVector(jcas, doc);
		}

	}
	/**
	 * 
	 * @param jcas
	 * @param doc
	 */

	private void createTermFreqVector(JCas jcas, Document doc) {

		String docText = doc.getText();
		String[] words = docText.split("\\s");
		Hashtable<String, Integer> table = new Hashtable<String, Integer>();
		Morphology mor = new Morphology();
		for (String word : words){
		  word = word.toLowerCase();
//		  if (VectorSpaceRetrieval.stopwords.contains(word))
//		    continue;
		  word = mor.stem(word);
		  if (table.containsKey(word)){
		    table.put(word, table.get(word) + 1);
		  }else{
		    table.put(word, 1);
		  }
		}
		List<Token> list = new ArrayList<Token>();
		for (String word : table.keySet()){
		  Token token = new Token(jcas);
		  token.setText(word);
		  token.setFrequency(table.get(word));
		  list.add(token);
		}
		doc.setTokenList(Utils.fromCollectionToFSList(jcas, list));
	}
}
