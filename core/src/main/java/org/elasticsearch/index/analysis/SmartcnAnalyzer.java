/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.index.analysis;
import org.apache.lucene.analysis.Analyzer;  
import org.apache.lucene.analysis.CharArraySet;  
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
<<<<<<< Updated upstream

=======
>>>>>>> Stashed changes
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.WordlistLoader;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.util.IOUtils;

public final class SmartcnAnalyzer extends Analyzer {
    private final CharArraySet stopWords;
    
    private static final String DEFAULT_STOPWORD_FILE = "stopwords.txt";
    
    private static final String STOPWORD_FILE_COMMENT = "//";

    public static CharArraySet getDefaultStopSet(){
      return DefaultSetHolder.DEFAULT_STOP_SET;
    }
    
    /**
     * Create a new Analyzer, using stopword list.
     */
    private static class DefaultSetHolder {
      static final CharArraySet DEFAULT_STOP_SET;

      static {
        try {
          DEFAULT_STOP_SET = loadDefaultStopWordSet();
        } catch (IOException ex) {
          throw new RuntimeException("Unable to load default stopword set");
        }
      }

      static CharArraySet loadDefaultStopWordSet() throws IOException {
        return CharArraySet.unmodifiableSet(WordlistLoader.getWordSet(IOUtils
            .getDecodingReader(SmartcnAnalyzer.class, DEFAULT_STOPWORD_FILE,
                StandardCharsets.UTF_8), STOPWORD_FILE_COMMENT));
      }
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        return null;
    }
    
    public SmartcnAnalyzer(CharArraySet charArraySet, CharArraySet charArraySet2){
        this(true);
    }

    public SmartcnAnalyzer(boolean useDefaultStopWords) {
        stopWords = useDefaultStopWords ? DefaultSetHolder.DEFAULT_STOP_SET
                : CharArraySet.EMPTY_SET;        
    }
    
    /**
     * Create a new Analyzer, using the provided {@link Set} of stopWords.
     */
    public SmartcnAnalyzer(CharArraySet stopWords) {
        this.stopWords = stopWords == null ? CharArraySet.EMPTY_SET : stopWords;
    }
    
    public TokenStreamComponents createComponents1(String fieldName){
        final Tokenizer tokenizer = new StandardTokenizer();
        TokenStream result = tokenizer;
        result = new PorterStemFilter(result);
        if (!stopWords.isEmpty()) {
          result = new StopFilter(result, stopWords);
        }        
        return new TokenStreamComponents(tokenizer, result);
    }
}
