package discomt.tools;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;

import java.io.Reader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;

public class AlignedCorpus {
	private MonolingualCorpusView source_;
	private MonolingualCorpusView target_;

	public class MonolingualCorpusView {
		private boolean isSource_;
		private	String[] elements_;
		private int[] alignmentIndex_;
		private int[] alignments_;
		private int[] sentenceStart_;

		private MonolingualCorpusView(boolean isSource,
				String[] elements, int[] alignmentIndex, int[] alignments, int[] sentenceStart) {
			isSource_ = isSource;
			elements_ = elements;
			alignmentIndex_ = alignmentIndex;
			alignments_ = alignments;
			sentenceStart_ = sentenceStart;
		}

		public String getElement(int id) {
			return elements_[id];
		}

		public String[] getElements() {
			return elements_;
		}

		public int getSize() {
			return elements_.length;
		}

		public int getNumberOfSentences() {
			return sentenceStart_.length - 1;
		}

		public String[] getAlignedElements(int id) {
			int nalig = alignmentIndex_[id+1] - alignmentIndex_[id];
			String[] al = new String[nalig];
			MonolingualCorpusView o = getOtherSide();

			for(int i = 0; i < nalig; i++)
				al[i] = o.getElement(alignments_[alignmentIndex_[id] + i]);

			return al;
		}

		public int[] getAlignedIDs(int id) {
			return Arrays.copyOfRange(alignments_, alignmentIndex_[id], alignmentIndex_[id+1]);
		}

		public MonolingualCorpusView getOtherSide() {
			if(isSourceView())
				return getTarget();
			else
				return getSource();
		}

		public boolean isSourceView() {
			return isSource_;
		}

		public int getSentenceStart(int no) {
			return sentenceStart_[no];
		}

		public int getSentenceEnd(int no) {
			return sentenceStart_[no+1];
		}

		public int findSentence(int index) {
			int n = Arrays.binarySearch(sentenceStart_, index);
			if(n >= 0)
				return n;
			else
				return -n - 2;
		}

		public String getSentenceAsString(int no) {
			return getSentenceAsString(no, new TIntHashSet());
		}

		public String getSentenceAsString(int no, int highlight) {
			int[] a = new int[1];
			a[0] = highlight;
			return getSentenceAsString(no, new TIntHashSet(a));
		}

		public String getSentenceAsString(int no, int[] highlight) {
			return getSentenceAsString(no, new TIntHashSet(highlight));
		}

		public String getSentenceAsString(int no, TIntHashSet highlight) {
			StringBuilder str = new StringBuilder();
			for(int i = sentenceStart_[no]; i < sentenceStart_[no+1]; i++) {
				if(i > sentenceStart_[no])
					str.append(' ');
				if(highlight.contains(i))
					str.append('*');
				str.append(elements_[i]); 
				if(highlight.contains(i))
					str.append('*');
			}
			return str.toString();
		}
	}

	private AlignedCorpus(MonolingualCorpusView src, MonolingualCorpusView tgt) {
		source_ = src;
		target_ = tgt;
	}

	public static AlignedCorpus loadCorpus(String name) throws IOException {
		String src = System.getProperty("coref.srclang", "en");
		String tgt = System.getProperty("coref.tgtlang", "fr");
		LineNumberReader srcrd = new LineNumberReader(new FileReader(name + "." + src));
		LineNumberReader tgtrd = new LineNumberReader(new FileReader(name + "." + tgt));
		LineNumberReader aligrd = new LineNumberReader(new FileReader(name + "." + src + "-" + tgt + ".alig"));
		LineNumberReader filterrd = null;
		File filter = new File(name + "." + src + "-" + tgt + ".lines-retained");
		if(filter.exists())
			filterrd = new LineNumberReader(new FileReader(filter));
		return new AlignedCorpus(srcrd, tgtrd, aligrd, filterrd);
	}

	public static AlignedCorpus loadCorpus(Reader rsrc, Reader rtgt, Reader ralignments)
			throws IOException {
		LineNumberReader src = new LineNumberReader(rsrc);
		LineNumberReader tgt = new LineNumberReader(rtgt);
		LineNumberReader alignments = new LineNumberReader(ralignments);
		return new AlignedCorpus(src, tgt, alignments, null);
	}

	public static AlignedCorpus loadFilteredCorpus(Reader rsrc, Reader rtgt,
		Reader ralignments, Reader rlinesRetained)
			throws IOException {
		LineNumberReader src = new LineNumberReader(rsrc);
		LineNumberReader tgt = new LineNumberReader(rtgt);
		LineNumberReader alignments = new LineNumberReader(ralignments);
		LineNumberReader linesRetained = new LineNumberReader(rlinesRetained);
		return new AlignedCorpus(src, tgt, alignments, linesRetained);
	}

	private AlignedCorpus(LineNumberReader src, LineNumberReader tgt, LineNumberReader alignments,
		LineNumberReader linesRetained)
			throws IOException {
		TIntArrayList srcSentenceStart = new TIntArrayList();
		TIntArrayList tgtSentenceStart = new TIntArrayList();
		ArrayList<String> srctext = new ArrayList<String>();
		ArrayList<String> tgttext = new ArrayList<String>();
		TIntArrayList srcAlignmentIndex = new TIntArrayList();
		TIntArrayList tgtAlignmentIndex = new TIntArrayList();
		TIntArrayList srcAlignments = new TIntArrayList();
		TIntArrayList tgtAlignments = new TIntArrayList();

		int[] noAlignments = new int[0];

		int s = 0, sindex = 0;
		int t = 0, tindex = 0;
		ArrayList<TIntArrayList> srca = new ArrayList<TIntArrayList>();
		ArrayList<TIntArrayList> tgta = new ArrayList<TIntArrayList>();

		int lineno = 1;
		int nextAlignedLine = -1;
		if(linesRetained != null)
			nextAlignedLine = Integer.parseInt(linesRetained.readLine());

		for(;;) {
			String srcline = src.readLine();
			String tgtline = tgt.readLine();

			String aligline = "";
			String linenoline = null;
			if(linesRetained == null)
				aligline = alignments.readLine();
			else if(nextAlignedLine > 0 && lineno == nextAlignedLine) {
				aligline = alignments.readLine();
				linenoline = linesRetained.readLine();
				if(linenoline != null)
					nextAlignedLine = Integer.parseInt(linenoline);
				else
					nextAlignedLine = -1;
			}

			srcSentenceStart.add(s);
			tgtSentenceStart.add(t);
			
			if(srcline == null && tgtline == null && linenoline == null)
				break;

			if(srcline == null || tgtline == null || (linesRetained == null && aligline == null)) {
				System.err.println("srcline " + String.valueOf(srcline));
				System.err.println("tgtline " + String.valueOf(tgtline));
				System.err.println("aligline " + String.valueOf(aligline));
				System.err.println("linenoline " + String.valueOf(aligline));
				throw new IOException("Corpus input files don't match (line: " +
						src.getLineNumber() + ")");
			}

			String[] srctok = srcline.split(" ");
			srctext.addAll(Arrays.asList(srctok));
			for(int i = 0; i < srctok.length; i++) {
				if(i >= srca.size())
					srca.add(new TIntArrayList());
				else
					srca.get(i).clear();
			}

			String[] tgttok = tgtline.split(" ");
			tgttext.addAll(Arrays.asList(tgttok));
			for(int i = 0; i < tgttok.length; i++) {
				if(i >= tgta.size())
					tgta.add(new TIntArrayList());
				else
					tgta.get(i).clear();
			}

			String[] links = aligline.split(" ");
			if(aligline != "") {
				for(int i = 0; i < links.length; i++) {
					String[] st = links[i].split("-");
					if(st.length != 2)
						throw new IOException("Invalid format in alignment file: " + aligline +
							" (line: " + alignments.getLineNumber() + ")");

					int sl = Integer.parseInt(st[0]);
					int tl = Integer.parseInt(st[1]);

					srca.get(sl).add(t + tl);
					tgta.get(tl).add(s + sl);
				}
			}

			for(int i = 0; i < srctok.length; i++) {
				srcAlignmentIndex.add(sindex);
				sindex += srca.get(i).size();
				srcAlignments.add(srca.get(i).toNativeArray());
			}

			for(int i = 0; i < tgttok.length; i++) {
				tgtAlignmentIndex.add(tindex);
				tindex += tgta.get(i).size();
				tgtAlignments.add(tgta.get(i).toNativeArray());
			}

			s += srctok.length;
			t += tgttok.length;
			lineno++;
		}

		srcAlignmentIndex.add(sindex);
		tgtAlignmentIndex.add(tindex);

		source_ = new MonolingualCorpusView(true, srctext.toArray(new String[0]),
			srcAlignmentIndex.toNativeArray(), srcAlignments.toNativeArray(), srcSentenceStart.toNativeArray());
		target_ = new MonolingualCorpusView(false, tgttext.toArray(new String[0]),
			tgtAlignmentIndex.toNativeArray(), tgtAlignments.toNativeArray(), tgtSentenceStart.toNativeArray());
	}

	public static AlignedCorpus loadMTOutput(Reader rsrc, Reader rout, int alignmentFactor)
			throws IOException {
		LineNumberReader src = new LineNumberReader(rsrc);
		LineNumberReader out = new LineNumberReader(rout);
		return new AlignedCorpus(src, out, alignmentFactor);
	}

	private AlignedCorpus(LineNumberReader src, LineNumberReader out, int alignmentFactor)
			throws IOException {
		TIntArrayList srcSentenceStart = new TIntArrayList();
		TIntArrayList tgtSentenceStart = new TIntArrayList();
		ArrayList<String> srctext = new ArrayList<String>();
		ArrayList<String> tgttext = new ArrayList<String>();
		TIntArrayList srcAlignmentIndex = new TIntArrayList();
		TIntArrayList tgtAlignmentIndex = new TIntArrayList();
		TIntArrayList srcAlignments = new TIntArrayList();
		TIntArrayList tgtAlignments = new TIntArrayList();

		int[] noAlignments = new int[0];

		int s = 0, sindex = 0;
		int t = 0, tindex = 0;
		ArrayList<TIntArrayList> srca = new ArrayList<TIntArrayList>();
		ArrayList<TIntArrayList> tgta = new ArrayList<TIntArrayList>();
		ArrayList<String> curphr = new ArrayList<String>();
		for(;;) {
			String srcline = src.readLine();
			String outline = out.readLine();

			srcSentenceStart.add(s);
			tgtSentenceStart.add(t);
			
			if(srcline == null && outline == null)
				break;

			if(srcline == null || outline == null)
				throw new IOException("Corpus input files don't match.");

			String[] srctok = srcline.split(" ");
			srctext.addAll(Arrays.asList(srctok));
			for(int i = 0; i < srctok.length; i++) {
				if(i >= srca.size())
					srca.add(new TIntArrayList());
				else
					srca.get(i).clear();
			}

			String[] outtok = outline.split(" ");
			int word = 0;
			int tgtphr = 0;
			for(String ttok : outtok) {
				if(!ttok.startsWith("|")) {
					String[] f = ttok.split("\\|");
					tgttext.add(f[0]);
					curphr.add(f[alignmentFactor]);
					if(word >= tgta.size())
						tgta.add(new TIntArrayList());
					else
						tgta.get(word).clear();
					word++;
				} else {
					int srcphr = Integer.parseInt(ttok.substring(1, ttok.indexOf('-')));
					for(int tl = 0; tl < curphr.size(); tl++) {
						if(curphr.get(tl).equals("-"))
							continue;
						String[] al = curphr.get(tl).split(",");
						for(String a : al) {
							try {
								int sl = Integer.parseInt(a);
								tgta.get(tgtphr + tl).add(s + srcphr + sl);
								srca.get(srcphr + sl).add(t + tgtphr + tl);
							} catch(NumberFormatException e) {}
						}
					}
					tgtphr += curphr.size();
					curphr.clear();
				}
			}

			for(int i = 0; i < srctok.length; i++) {
				srcAlignmentIndex.add(sindex);
				sindex += srca.get(i).size();
				srcAlignments.add(srca.get(i).toNativeArray());
			}

			for(int i = 0; i < word; i++) {
				tgtAlignmentIndex.add(tindex);
				tindex += tgta.get(i).size();
				tgtAlignments.add(tgta.get(i).toNativeArray());
			}

			s += srctok.length;
			t += word;
		}

		srcAlignmentIndex.add(sindex);
		tgtAlignmentIndex.add(tindex);

		source_ = new MonolingualCorpusView(true, srctext.toArray(new String[0]),
			srcAlignmentIndex.toNativeArray(), srcAlignments.toNativeArray(), srcSentenceStart.toNativeArray());
		target_ = new MonolingualCorpusView(false, tgttext.toArray(new String[0]),
			tgtAlignmentIndex.toNativeArray(), tgtAlignments.toNativeArray(), tgtSentenceStart.toNativeArray());
	}

	public MonolingualCorpusView getSource() {
		return source_;
	}

	public MonolingualCorpusView getTarget() {
		return target_;
	}
}
