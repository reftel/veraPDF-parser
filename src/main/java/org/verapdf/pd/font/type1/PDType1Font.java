/**
 * This file is part of veraPDF Parser, a module of the veraPDF project.
 * Copyright (c) 2015, veraPDF Consortium <info@verapdf.org>
 * All rights reserved.
 *
 * veraPDF Parser is free software: you can redistribute it and/or modify
 * it under the terms of either:
 *
 * The GNU General public license GPLv3+.
 * You should have received a copy of the GNU General Public License
 * along with veraPDF Parser as the LICENSE.GPL file in the root of the source
 * tree.  If not, see http://www.gnu.org/licenses/ or
 * https://www.gnu.org/licenses/gpl-3.0.en.html.
 *
 * The Mozilla Public License MPLv2+.
 * You should have received a copy of the Mozilla Public License along with
 * veraPDF Parser as the LICENSE.MPL file in the root of the source tree.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package org.verapdf.pd.font.type1;

import org.verapdf.as.ASAtom;
import org.verapdf.as.io.ASInputStream;
import org.verapdf.as.io.ASMemoryInStream;
import org.verapdf.cos.*;
import org.verapdf.parser.COSParser;
import org.verapdf.pd.font.Encoding;
import org.verapdf.pd.font.FontProgram;
import org.verapdf.pd.font.PDFontDescriptor;
import org.verapdf.pd.font.PDSimpleFont;
import org.verapdf.pd.font.cff.CFFFontProgram;
import org.verapdf.pd.font.opentype.OpenTypeFontProgram;
import org.verapdf.pd.font.stdmetrics.StandardFontMetrics;
import org.verapdf.pd.font.stdmetrics.StandardFontMetricsFactory;
import org.verapdf.pd.font.truetype.TrueTypePredefined;
import org.verapdf.tools.StaticResources;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class represents Type 1 font on PD level.
 *
 * @author Sergey Shemyakov
 */
public class PDType1Font extends PDSimpleFont {

    private static final Logger LOGGER = Logger.getLogger(PDType1Font.class.getCanonicalName());
    public static final ASAtom[] STANDARD_FONT_NAMES = {
            ASAtom.COURIER_BOLD,
            ASAtom.COURIER_BOLD_OBLIQUE,
            ASAtom.COURIER,
            ASAtom.COURIER_OBLIQUE,
            ASAtom.HELVETICA,
            ASAtom.HELVETICA_BOLD,
            ASAtom.HELVETICA_BOLD_OBLIQUE,
            ASAtom.HELVETICA_OBLIQUE,
            ASAtom.SYMBOL,
            ASAtom.TIMES_BOLD,
            ASAtom.TIMES_BOLD_ITALIC,
            ASAtom.TIMES_ITALIC,
            ASAtom.TIMES_ROMAN,
            ASAtom.ZAPF_DINGBATS};

    private Boolean isStandard = null;
    private StandardFontMetrics fontMetrics;

    /**
     * Constructor from type 1 font dictionary.
     * @param dictionary is type 1 font dictionary.
     */
    public PDType1Font(COSDictionary dictionary) {
        super(dictionary);
        if (isNameStandard() && this.fontDescriptor.getObject().size() == 0) {
            fontMetrics = StandardFontMetricsFactory.getFontMetrics(this.getName());
            this.fontDescriptor = PDFontDescriptor.getDescriptorFromMetrics(fontMetrics);
        }
    }

    /**
     * @return set of character names defined in font as specified in CIDSet in
     * font descriptor.
     */
    public Set<String> getDescriptorCharSet() {
        String descriptorCharSetString = this.fontDescriptor.getCharSet();
        if (descriptorCharSetString != null) {
            try {
                ASMemoryInStream stream =
                        new ASMemoryInStream(descriptorCharSetString.getBytes());
                Set<String> descriptorCharSet = new TreeSet<>();
                COSParser parser = new COSParser(stream);
                COSObject glyphName = parser.nextObject();
                while (!glyphName.empty()) {
                    if (glyphName.getType() == COSObjType.COS_NAME) {
                        descriptorCharSet.add(glyphName.getString());
                    }
                    glyphName = parser.nextObject();
                }
                return descriptorCharSet;
            } catch (IOException ex) {
                LOGGER.log(Level.FINE, "Can't parse /CharSet entry in Type 1 font descriptor", ex);
                return Collections.emptySet();
            }
        }
        return Collections.emptySet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FontProgram getFontProgram() {
        if (!this.isFontParsed) {
            this.isFontParsed = true;
            if (fontDescriptor.canParseFontFile(ASAtom.FONT_FILE)) {
                COSStream type1FontFile = fontDescriptor.getFontFile();
                COSKey key = type1FontFile.getObjectKey();
                this.fontProgram = StaticResources.getCachedFont(key);
                if (fontProgram == null) {
                    try (ASInputStream fontData = type1FontFile.getData(COSStream.FilterFlags.DECODE)) {
                        this.fontProgram = new Type1FontProgram(fontData);
                        StaticResources.cacheFontProgram(key, this.fontProgram);
                    } catch (IOException e) {
                        LOGGER.log(Level.FINE, "Can't read Type 1 font program.", e);
                        return null;
                    }
                }
            } else if (fontDescriptor.canParseFontFile(ASAtom.FONT_FILE3)) {
                COSStream type1FontFile = fontDescriptor.getFontFile3();
                ASAtom subtype = type1FontFile.getNameKey(ASAtom.SUBTYPE);
                COSKey key = type1FontFile.getObjectKey();
                this.fontProgram = StaticResources.getCachedFont(key);
                if (fontProgram == null) {
                    try (ASInputStream fontData = type1FontFile.getData(COSStream.FilterFlags.DECODE)) {
                        if (subtype == ASAtom.TYPE1C) {

                            this.fontProgram = new CFFFontProgram(fontData, null, this.isSubset());
                        } else if (subtype == ASAtom.OPEN_TYPE) {
                            this.fontProgram = new OpenTypeFontProgram(fontData, true, this.isSymbolic(),
                                    this.getEncoding(), null, this.isSubset());
                        }
                        StaticResources.cacheFontProgram(key, this.fontProgram);
                    } catch (IOException e) {
                        LOGGER.log(Level.FINE, "Can't read Type 1 font program.", e);
                        return null;
                    }
                }
            } else {
                this.fontProgram = null;
            }
        }
        return this.fontProgram;
    }

    /**
     * @return true if this font is one of standard 14 fonts.
     */
    public Boolean isStandard() {
        if (this.isStandard == null) {
            if (!containsDiffs() && !isEmbedded() && isNameStandard()) {
                isStandard = Boolean.valueOf(true);
                return isStandard;
            }
            isStandard = Boolean.valueOf(false);
            return isStandard;
        }
        return this.isStandard;
    }

    private boolean containsDiffs() {
        if (this.dictionary.getKey(ASAtom.ENCODING).getType() ==
                COSObjType.COS_DICT) {
            Map<Integer, String> differences = this.getDifferences();
            if (differences != null && differences.size() != 0) {
                String[] baseEncoding = getBaseEncoding((COSDictionary)
                        this.dictionary.getKey(ASAtom.ENCODING).getDirectBase());
                if (baseEncoding.length == 0) {
                    return true;
                }
                for (Map.Entry<Integer, String> entry : differences.entrySet()) {
                    if (!entry.getValue().equals(baseEncoding[entry.getKey().intValue()])) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String[] getBaseEncoding(COSDictionary encoding) {
        ASAtom baseEncoding = encoding.getNameKey(ASAtom.BASE_ENCODING);
        if (baseEncoding == null) {
            return new String[]{};
        }
        if (baseEncoding == ASAtom.MAC_ROMAN_ENCODING) {
            return Arrays.copyOf(TrueTypePredefined.MAC_ROMAN_ENCODING,
                    TrueTypePredefined.MAC_ROMAN_ENCODING.length);
        } else if (baseEncoding == ASAtom.MAC_EXPERT_ENCODING) {
            return Arrays.copyOf(TrueTypePredefined.MAC_EXPERT_ENCODING,
                    TrueTypePredefined.MAC_EXPERT_ENCODING.length);
        } else if (baseEncoding == ASAtom.WIN_ANSI_ENCODING) {
            return Arrays.copyOf(TrueTypePredefined.WIN_ANSI_ENCODING,
                    TrueTypePredefined.WIN_ANSI_ENCODING.length);
        } else {
            return new String[]{};
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Double getWidth(int code) {
        if (getFontProgram() != null) {
            return super.getWidth(code);
        }
        if (fontMetrics != null) {
            StandardFontMetrics metrics =
                    StandardFontMetricsFactory.getFontMetrics(this.getName());
            Encoding enc = this.getEncodingMapping();
            if (metrics != null) {
                return Double.valueOf(metrics.getWidth(enc.getName(code)));
            }
        }
        // should not get here
        LOGGER.log(Level.FINE, "Can't get standard metrics");
        return null;
    }

    @Override
    public float getWidthFromProgram(int code) {
        Encoding pdEncoding = this.getEncodingMapping();
        if (pdEncoding != null) {
            String glyphName = pdEncoding.getName(code);
            if (glyphName != null) {
                return this.getFontProgram().getWidth(glyphName);
            }
        }
        return this.getFontProgram().getWidth(code);
    }

    @Override
    public boolean glyphIsPresent(int code) {
        Encoding pdEncoding = this.getEncodingMapping();
        if (pdEncoding != null) {
            String glyphName = pdEncoding.getName(code);
            if (glyphName != null) {
                return this.getFontProgram().containsGlyph(glyphName);
            }
        }
        return this.getFontProgram().containsCode(code);
    }

    private boolean isEmbedded() {
        return this.getFontProgram() != null;
    }

    private boolean isNameStandard() {
        ASAtom fontName = this.getDictionary().getNameKey(ASAtom.BASE_FONT);
        for (ASAtom standard : STANDARD_FONT_NAMES) {
            if (standard == fontName) {
                return true;
            }
        }
        return false;
    }

    public String toUnicodePDFA1(int code) {
        String unicodeString = super.cMapToUnicode(code);
        if(unicodeString != null) {
            return unicodeString;
        }
        Encoding fontEncoding = this.getEncodingMapping();
        String glyphName =  null;
        if (fontEncoding != null) {
            glyphName = fontEncoding.getName(code);
        }
        if (glyphName == null && getFontProgram() != null) {
            glyphName = fontProgram.getGlyphName(code);
        }
        if (glyphName != null) {
            if (Arrays.asList(TrueTypePredefined.STANDARD_ENCODING).contains(glyphName) || SymbolSet.hasGlyphName(glyphName)) {
                return " "; // indicates that toUnicode should not be checked.
            }
            return null;
        }
        LOGGER.log(Level.FINE, "Cannot find encoding for glyph with code" + code + " in font " + this.getName());
        return null;
    }

}
