package discomt.eval;

import discomt.tools.AlignedCorpus;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntProcedure;
import gnu.trove.TObjectIntHashMap;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class PronounEvaluation {
	private final static int verbosity_ = Integer.parseInt(System.getProperty("eval.verbosity", "0"));

	private AlignedCorpus reference_;
	private AlignedCorpus candidate_;

	private TIntArrayList docBoundaries_;
	private ArrayList<DocumentSummary> summaries_;

	public PronounEvaluation(String reffile, String candfile, String docfile) throws IOException {
		reference_ = AlignedCorpus.loadCorpus(reffile);
		candidate_ = AlignedCorpus.loadCorpus(candfile);

		docBoundaries_ = new TIntArrayList();
		summaries_ = new ArrayList<DocumentSummary>();

		BufferedReader docrd = new BufferedReader(new FileReader(docfile));
		String line;
		int docno = 0;
		while((line = docrd.readLine()) != null) {
			String[] f = line.split("\\s");

			String docid = null;
			if(f.length == 2)
				docid = f[1];
			else if(f.length == 1)
				docid = "Document " + Integer.toString(docno);
			else {
				System.err.println("Invalid line in doc boundary file: " + line);
				System.exit(1);
			}

			docBoundaries_.add(reference_.getSource().getSentenceStart(Integer.parseInt(f[0])));
			summaries_.add(new DocumentSummary(docid));
			docno++;
		}
		docBoundaries_.add(reference_.getSource().getSize());
	}

	public List<DocumentSummary> evaluate() {
		int docno = 0;
		int lastsentence = -1;
		TObjectIntHashMap refwords = new TObjectIntHashMap();
		for(int cidx = 0; cidx < reference_.getSource().getSize(); cidx++) {
			while(cidx >= docBoundaries_.get(docno + 1))
				docno++;

			String srctoken = reference_.getSource().getElement(cidx);

			if(!isTriggerWord(srctoken))
				continue;

			// lowercase after checking so the abbreviation IT doesn't count as it
			srctoken = srctoken.toLowerCase();

			String[] reftgt = reference_.getSource().getAlignedElements(cidx);
			refwords.clear();
			for(String r : reftgt) {
				refwords.adjustOrPutValue(r, 1, 1);
				summaries_.get(docno).refoccurrences.adjustOrPutValue(srctoken, 1, 1);
			}

			String[] candtgt = candidate_.getSource().getAlignedElements(cidx);

			for(String c : candtgt) {
				summaries_.get(docno).candoccurrences.adjustOrPutValue(srctoken, 1, 1);
				if(refwords.get(c) > 0) {
					summaries_.get(docno).matches.adjustOrPutValue(srctoken, 1, 1);
					refwords.adjustValue(c, -1);
				}
			}

			if(verbosity_ >= 2) {
				int snt = reference_.getSource().findSentence(cidx);
				if(snt > lastsentence) {
					System.out.println();
					System.out.println(reference_.getSource().getSentenceAsString(snt, cidx));
					System.out.println(reference_.getTarget().getSentenceAsString(snt));
					System.out.println(candidate_.getTarget().getSentenceAsString(snt));
					lastsentence = snt;
				}

				System.out.print(srctoken + " ||| ");
				for(int i = 0; i < reftgt.length; i++) {
					if(i > 0)
						System.out.print(" | ");
					System.out.print(reftgt[i]);
				}
				System.out.print(" ||| ");
				for(int i = 0; i < candtgt.length; i++) {
					if(i > 0)
						System.out.print(" | ");
					System.out.print(candtgt[i]);
				}
				System.out.println();
			}
		}

		return summaries_;
	}

	private boolean isTriggerWord(String w) {
		return w.equals("it") || w.equals("they") || w.equals("It") || w.equals("They");
	}

	public static void main(String[] args) throws IOException {
		if(args.length != 3) {
			System.err.println("Usage: PronounEvaluation reference candidate doc-boundaries");
			System.exit(1);
		}

		PronounEvaluation evalobj = new PronounEvaluation(args[0], args[1], args[2]);
		List<DocumentSummary> eval = evalobj.evaluate();

		HashSet<String> pset = new HashSet<String>();
		for(DocumentSummary s : eval)
			pset.addAll(Arrays.asList(s.candoccurrences.keys(new String[0])));
		String[] pronouns = pset.toArray(new String[0]);
		Arrays.sort(pronouns);

		verbose(1, "                    ");
		for(String p : pronouns)
			verbose(1, String.format("%9s   ", p));
		verbose(1, "\n");

		TObjectIntHashMap<String> pronMatches = new TObjectIntHashMap<String>();
		TObjectIntHashMap<String> pronOccurrences = new TObjectIntHashMap<String>();

		float ratio;
		int totalMatches = 0;
		int totalOccurrences = 0;

		class CandCounter implements TIntProcedure {
			public int count = 0;

			public boolean execute(int v) {
				count += v;
				return true;
			}
		}

		CandCounter candCounter = new CandCounter();

		for(DocumentSummary s : eval) {
			int docMatches = 0;
			int docOccurrences = 0;
			verbose(1, String.format("%18s  ", s.docid));
			for(String p : pronouns) {
				int m = s.matches.get(p);
				int o = s.refoccurrences.get(p);
				if(o > 0)
					verbose(1, String.format("%4d/%4d   ", m, o));
				else
					verbose(1, "   -/   -   ");
				pronMatches.adjustOrPutValue(p, m, m);
				pronOccurrences.adjustOrPutValue(p, o, o);
				docMatches += m;
				docOccurrences += o;
			}
			ratio = ((float) docMatches) / ((float) docOccurrences);
			verbose(1, String.format("%4d/%4d   %.4f\n", docMatches, docOccurrences, ratio));
			totalMatches += docMatches;
			totalOccurrences += docOccurrences;

			s.candoccurrences.forEachValue(candCounter);
		}
		int totalCandOccurrences = candCounter.count;

		verbose(1, "                    ");
		for(String p : pronouns) {
			int m = pronMatches.get(p);
			int o = pronOccurrences.get(p);
			verbose(1, String.format("%4d/%4d   ", m, o));
		}
		float recall = ((float) totalMatches) / ((float) totalOccurrences);
		verbose(1, String.format("%4d/%4d   %.4f\n\n", totalMatches, totalOccurrences, recall));

		float precision = ((float) totalMatches) / ((float) totalCandOccurrences);
		float fscore = 2f * precision * recall / (precision + recall);

		System.out.println(String.format("Precision:   %4d/%4d    %.4f",
			totalMatches, totalCandOccurrences, precision));
		System.out.println(String.format("Recall:      %4d/%4d    %.4f",
			totalMatches, totalOccurrences, recall));
		System.out.println(String.format("F1:                       %.4f", fscore));
	}

	private static void verbose(int level, String s) {
		if(verbosity_ >= level)
			System.out.print(s);
	}

	private class DocumentSummary {
		public String docid;
		public TObjectIntHashMap<String> refoccurrences = new TObjectIntHashMap<String>();
		public TObjectIntHashMap<String> candoccurrences = new TObjectIntHashMap<String>();
		public TObjectIntHashMap<String> matches = new TObjectIntHashMap<String>();

		public DocumentSummary(String id) {
			docid = id;
		}
	}
}
