package org.elasticsearch.index.analysis;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.elasticsearch.analysis.PinyinConfig;
import org.nlpcn.commons.lang.pinyin.Pinyin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PinyinTokenFilter extends TokenFilter {

    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private boolean done = true;
    private boolean processedCandidate = false;
    private boolean processedFirstLetter = false;
    private boolean processedOriginal = false;
    protected int position = 0;
    protected int lastPosition = 0;
    private PinyinConfig config;
    List<String> candidate;
    StringBuilder firstLetters;
    String source;

    public PinyinTokenFilter(TokenStream in, PinyinConfig config) {
        super(in);
        this.config = config;
        //validate config
        if (!(config.keepFirstLetter || config.keepFullPinyin)) {
            throw new ConfigErrorException("pinyin config error, can't disable first_letter and full_pinyin at the same time.");
        }
        candidate = new ArrayList<>();
        firstLetters = new StringBuilder();
    }

    @Override
    public final boolean incrementToken() throws IOException {


        if (!done) {
            if (readTerm()) return true;
        }

        if (done) {
            resetVariable();
            if (!input.incrementToken()) {
                return false;
            }
            done = false;
        }
        readTerm();
        return true;
    }

    private boolean readTerm() {
        if (!processedCandidate) {
            processedCandidate = true;
            final int bufferLength = termAtt.length();
            source = termAtt.toString();
            lastPosition = bufferLength;
            source = termAtt.toString();
            if (config.trimWhitespace) {
                source = source.trim();
            }

            List<String> pinyinList = Pinyin.pinyin(source);

            StringBuilder buff = new StringBuilder();
            StringBuilder termFullPinYinBuff = new StringBuilder();

            for (int i = 0; i < source.length(); i++) {
                char c = source.charAt(i);
                //keep original alphabet
                if (c < 128) {
                    if ((c > 96 && c < 123) || (c > 64 && c < 91) || (c > 47 && c < 58)) {
                        if (config.keepNoneChinese) {
                            if (config.keepNoneChineseTogether) {
                                buff.append(c);
                            } else {
                                candidate.add(String.valueOf(c));
                            }
                        }
                        if (config.keepNoneChineseInFirstLetter) {
                            firstLetters.append(c);
                        }
                    }
                } else {
                    //clean previous temp
                    if (buff.length() > 0) {
                        cleanBuff(buff);
                    }
                    try{
                        String pinyin = pinyinList.get(i);
                        if (pinyin != null && pinyin.length() > 0) {

                            firstLetters.append(pinyin.charAt(0));
                            if (config.keepSeparateFirstLetter & pinyin.length() > 1) {
                                candidate.add(String.valueOf(pinyin.charAt(0)));
                            }
                            if (config.keepFullPinyin) {
                                if(config.splitFullPinYin){
                                    candidate.add(pinyin);
                                }else{
                                    termFullPinYinBuff.append(pinyin);
                                }
                            }
                        }
                    }catch(Exception e){
                        System.out.println("unrecongnized pinyin chars:" + String.valueOf(c));
                    }
                    
                }
            }

            //clean previous temp
            if (buff.length() > 0) {
                cleanBuff(buff);
            }
            if (termFullPinYinBuff.length() > 0) {
                candidate.add(termFullPinYinBuff.toString());
                termFullPinYinBuff.setLength(0);
            }
        }


        if (position < candidate.size()) {
            String s = candidate.get(position);
            if (config.lowercase) {
                s = s.toLowerCase();
            }
            termAtt.setEmpty();
            termAtt.append(s);
            position++;
            return true;
        }


        if (config.keepOriginal && !processedOriginal) {
            processedOriginal = true;
            termAtt.setEmpty();
            termAtt.append(source);
            termAtt.setLength(source.length());
            return true;
        }


        if (config.keepFirstLetter && firstLetters.length() > 0 && !processedFirstLetter) {
            processedFirstLetter = true;
            String fl;
            if (firstLetters.length() > config.LimitFirstLetterLength && config.LimitFirstLetterLength > 0) {
                fl = firstLetters.substring(0, config.LimitFirstLetterLength);
            } else {
                fl = firstLetters.toString();
            }
            if (config.lowercase) {
                fl = fl.toLowerCase();
            }
            if (!(config.keepSeparateFirstLetter && fl.length() <= 1)) {
                termAtt.setEmpty();
                termAtt.append(fl);
                termAtt.setLength(fl.length());
                return true;
            }

        }

        done = true;
        return false;
    }

    private void cleanBuff(StringBuilder buff) {
        if (config.keepNoneChinese) {
            if (config.noneChinesePinyinTokenize) {
                List<String> temp = PinyinAlphabetTokenizer.walk(buff.toString());
                for (int j = 0; j < temp.size(); j++) {
                    String tmp = temp.get(j);
                    candidate.add(tmp);
                }
            } else {
                candidate.add(buff.toString());
            }
        }

        buff.setLength(0);
    }


    @Override
    public final void end() throws IOException {
        super.end();
    }

    void resetVariable() {
        position = 0;
        lastPosition = 0;
        candidate.clear();
        this.processedCandidate = false;
        this.processedFirstLetter = false;
        this.processedOriginal = false;
        firstLetters.setLength(0);
        candidate.clear();
        source = null;
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        this.done = true;
        resetVariable();
    }


}
