package basemod.patches.com.megacrit.cardcrawl.helpers.FontHelper;

import basemod.ReflectionHacks;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.evacipated.cardcrawl.modthespire.lib.*;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.helpers.FontHelper;
import javassist.*;
import javassist.bytecode.LocalVariableAttribute;
import javassist.bytecode.MethodInfo;
import javassist.expr.Expr;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;
import javassist.expr.MethodCall;

import java.util.HashMap;
import java.util.Map;

public class FixChineseColoredTextMess {
	public static final Map<String, Color> COLOR_MAP = new HashMap<>();
	
	@SpirePatch(clz = FontHelper.class, method = "exampleNonWordWrappedText")
	public static class FixChineseNoColoredText {
		@SpireInsertPatch(locator = Locator.class, localvars = {"word"})
		public static void Insert(SpriteBatch sb, BitmapFont font, String msg, float x, float y, Color c, float widthMax, float lineSpacing, @ByRef float[] ___curWidth, @ByRef int[] ___currentLine, String word) {
			if (usingHexColor(word)) {
				int colorStart = 1;
				int dropStart = word.indexOf("]") + 1;
				String colorHex = word.substring(colorStart, dropStart - 1);
				Color fontColor;
				if (COLOR_MAP.containsKey(colorHex)) {
					fontColor = COLOR_MAP.get(colorHex);
				} else {
					fontColor = Color.valueOf(colorHex);
					COLOR_MAP.put(colorHex, fontColor);
				}
				fontColor.a = c.a;
				int dropEnd = word.lastIndexOf("[");
				String originalWord = word;
				word = word.substring(dropStart, dropEnd);
				Color oldColor = font.getColor().cpy();
				font.setColor(fontColor);
				for (int i = 0; i < word.length(); i++) {
					String j = Character.toString(word.charAt(i));
					FontHelper.layout.setText(font, j);
					___curWidth[0] += FontHelper.layout.width;
					if (___curWidth[0] > widthMax) {
						boolean newLine = MakeNewLineForColoredText(i, word, originalWord, msg);
						if (newLine) {
							___curWidth[0] = FontHelper.layout.width;
							___currentLine[0]++;
						}
					}
					font.draw(sb, j, x + ___curWidth[0] - FontHelper.layout.width, y - lineSpacing * ___currentLine[0]);
				}
				font.setColor(oldColor);
			}
		}

		private static class Locator extends SpireInsertLocator {
			@Override
			public int[] Locate(CtBehavior ctMethodToPatch) throws Exception {
				Matcher finalMatcher = new Matcher.MethodCallMatcher(FontHelper.class, "identifyOrb");
				return LineFinder.findInOrder(ctMethodToPatch, finalMatcher);
			}
		}
	}
	
	@SpirePatch2(clz = FontHelper.class, method = "exampleNonWordWrappedText")
	public static class FixChineseVanillaTextNewLinePatch {
		private static int paramIndex = 0;
		@SpireRawPatch
		public static void FixColoredTextAlwaysNewLine(CtBehavior ctBehavior) throws Exception {
			ClassPool pool = ctBehavior.getDeclaringClass().getClassPool();
			CtClass fontHelper = pool.get(FontHelper.class.getName());
			CtMethod enwwt = fontHelper.getDeclaredMethod("exampleNonWordWrappedText");
			MethodInfo methodInfo = enwwt.getMethodInfo();
			LocalVariableAttribute table = (LocalVariableAttribute) methodInfo.getCodeAttribute().getAttribute(LocalVariableAttribute.tag);
			Matcher.MethodCallMatcher setTextMatcher = new Matcher.MethodCallMatcher(GlyphLayout.class, "setText");
			Matcher.MethodCallMatcher setColorMatcher = new Matcher.MethodCallMatcher(BitmapFont.class, "setColor");
			Matcher.FieldAccessMatcher widthMatcher = new Matcher.FieldAccessMatcher(GlyphLayout.class, "width");
			
			// manipulated starting lines
			int boolLine = LineFinder.findAllInOrder(enwwt, setTextMatcher)[2];
			int[] allLines = LineFinder.findAllInOrder(enwwt, setColorMatcher);
			int fontLine = allLines[allLines.length - 1];
			int fixMethodLine = LineFinder.findAllInOrder(enwwt, widthMatcher)[1];
			String fixClass = FixChineseColoredTextMess.class.getName();
			String newLineFixClass = FixChineseVanillaTextNewLinePatch.class.getName();

			// add local vars to store values
			CtClass boolType = pool.get(boolean.class.getName());
			String usingFixMethodParam = getParamName(table, "usingFixMethod");
			enwwt.addLocalVariable(usingFixMethodParam, boolType);
			
			CtClass floatArrayType = pool.get(float[].class.getName());
			String curWidthArray = getParamName(table, "curWithArray");
			enwwt.addLocalVariable(curWidthArray, floatArrayType);
			
			CtClass intArrayType = pool.get(int[].class.getName());
			String curLineArray = getParamName(table, "curLineArray");
			enwwt.addLocalVariable(curLineArray, intArrayType);
			
			enwwt.insertBefore(usingFixMethodParam + "=false;" + curWidthArray + "=new float[1];"
					+ curLineArray + "=new int[1];");
			enwwt.insertAt(boolLine, "{" + usingFixMethodParam + "=" + fixClass + ".usingVanillaColorCode(word);"
					+ curWidthArray + "[0]=curWidth;" + curLineArray + "[0]=currentLine;}");
			// skip all vanilla things
			{
				setInstruments(enwwt, new ExprEditor(){
					@Override
					public void edit(MethodCall m) throws CannotCompileException {
						if ("setText".equals(m.getMethodName()) && m.getLineNumber() == boolLine) {
							m.replace("{if(!" + usingFixMethodParam + "){$_=$proceed($$);}}");
						}
					}
				}, new ExprEditor(){
					@Override
					public void edit(FieldAccess f) throws CannotCompileException {
						if ("curWidth".equals(f.getFieldName()) && f.getClassName().equals(FontHelper.class.getName())
								&& inRange(f, fixMethodLine, fontLine)) {
							f.replace("{if(!" + usingFixMethodParam + "){$_=$proceed($$);}}");
						}
					}
				}, new ExprEditor(){
					@Override
					public void edit(FieldAccess f) throws CannotCompileException {
						if ("currentLine".equals(f.getFieldName()) && f.getClassName().equals(FontHelper.class.getName())
								&& inRange(f, fixMethodLine, fontLine)) {
							f.replace("{if(!" + usingFixMethodParam + "){$_=$proceed($$);}}");
						}
					}
				}, new ExprEditor(){
					@Override
					public void edit(MethodCall m) throws CannotCompileException {
						if ("draw".equals(m.getMethodName()) && inRange(m, boolLine, fontLine)) {
							m.replace("{if(!" + usingFixMethodParam + "){$_=$proceed($$);}}");
						}
					}
				});
			}
			enwwt.insertAt(fixMethodLine, "{if(" + usingFixMethodParam + "){" + newLineFixClass + ".DoFix(sb, font, msg, x, y, c, widthMax, " +
					"lineSpacing," + curWidthArray + ", " + curLineArray + ", word);curWidth=" + curWidthArray + "[0];currentLine="
					+ curLineArray + "[0];}}");
		}
		
		public static void DoFix(SpriteBatch sb, BitmapFont font, String msg, float x, float y, Color c, float widthMax, float lineSpacing,
								 float[] ___curWidth, int[] ___currentLine, String word) {
			int colorStart = 1;
			int dropStart = word.indexOf("]") + 1;
			String colorHex = word.substring(colorStart, dropStart - 1);
			Color fontColor;
			if (COLOR_MAP.containsKey(colorHex)) {
				fontColor = COLOR_MAP.get(colorHex);
			} else {
				fontColor = Color.valueOf(colorHex);
				COLOR_MAP.put(colorHex, fontColor);
			}
			int dropEnd = word.lastIndexOf("[");
			String originalWord = word;
			word = word.substring(dropStart, dropEnd);
			fontColor.a = c.a;
			Color oldColor = font.getColor().cpy();
			font.setColor(fontColor);
			for (int i = 0; i < word.length(); i++) {
				String j = Character.toString(word.charAt(i));
				FontHelper.layout.setText(font, j);
				___curWidth[0] += FontHelper.layout.width;
				if (___curWidth[0] > widthMax) {
					boolean newLine = MakeNewLineForColoredText(i, word, originalWord, msg);
					if (newLine) {
						___curWidth[0] = FontHelper.layout.width;
						___currentLine[0]++;
					}
				}
				font.draw(sb, j, x + ___curWidth[0] - FontHelper.layout.width, y - lineSpacing * ___currentLine[0]);
			}
			font.setColor(oldColor);
		}
		
		@SpireRawPatch
		public static void FixRenderingNotColoredTextNewLineAtEndOfText(CtBehavior ctBehavior) throws Exception {
			ClassPool pool = ctBehavior.getDeclaringClass().getClassPool();
			CtClass fontHelper = pool.get(FontHelper.class.getName());
			CtMethod enwwt = fontHelper.getDeclaredMethod("exampleNonWordWrappedText");
			MethodInfo methodInfo = enwwt.getMethodInfo();
			
			// find the line of increment of currentLine in vanilla code
			// and the line of changing curWidth
			Matcher.FieldAccessMatcher currentLineMatcher = new Matcher.FieldAccessMatcher(FontHelper.class, "currentLine");
			int[] lines = LineFinder.findAllInOrder(enwwt, currentLineMatcher);
			int currentLineLine = lines[lines.length - 2];
			Matcher.FieldAccessMatcher curWidthMatcher = new Matcher.FieldAccessMatcher(FontHelper.class, "curWidth");
			lines = LineFinder.findAllInOrder(enwwt, curWidthMatcher);
			int curWidthLine = lines[lines.length - 2];
			
			enwwt.instrument(new ExprEditor(){
				@Override
				public void edit(FieldAccess f) throws CannotCompileException {
					if ("currentLine".equals(f.getFieldName()) && f.getLineNumber() == currentLineLine) {
						f.replace("{if(" + FixChineseColoredTextMess.class.getName()
								+ ".MakeNewLine(i,word,msg))$_=$proceed($$);}");
					}
					if ("curWidth".equals(f.getFieldName()) && f.getLineNumber() == curWidthLine) {
						f.replace("{if(" + FixChineseColoredTextMess.class.getName()
								+ ".MakeNewLine(i,word,msg))$_=$proceed($$);}");
					}
				}
			});
		}
		
		private static void setInstruments(CtMethod method, ExprEditor... exprs) throws CannotCompileException {
			for (ExprEditor e : exprs) {
				method.instrument(e);
			}
		}
		
		private static boolean inRange(Expr e, int min, int max) {
			return e.getLineNumber() >= min && e.getLineNumber() < max;
		}
		
		private static String getParamName(LocalVariableAttribute table, String key) {
			int index = (table != null ? table.length() : 0) + paramIndex;
			paramIndex++;
			return "_param_" + index + "_" + key;
		}
	}
	
	public static boolean MakeNewLine(int indexOfChar, String word, String msg) {
		// when the current char is a punctuation and is at the end
		// prevent currentLine auto increasing
		String[] strings = msg.split(" ");
		char currentChar = word.charAt(indexOfChar);
		boolean punctuation = isChinesePunctuation(currentChar);
		return !punctuation;
	}
	
	public static boolean MakeNewLineForColoredText(int indexOfChar, String modifiedWord, String originalWord, String msg) {
		String[] strings = msg.split(" ");
		char currentChar = modifiedWord.charAt(indexOfChar);
		boolean punctuation = isChinesePunctuation(currentChar);
		return !punctuation;
	}
	
	private static boolean isChinesePunctuation(char c) {
		Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
		return ub == Character.UnicodeBlock.GENERAL_PUNCTUATION
				|| ub == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
				|| ub == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
				|| ub == Character.UnicodeBlock.CJK_COMPATIBILITY_FORMS
				|| ub == Character.UnicodeBlock.VERTICAL_FORMS;
	}
	
	@SpirePatch2(clz = FontHelper.class, method = "getHeightForCharLineBreak")
	public static class FixGetHeightForChineseText {
		private static final float CARD_ENERGY_IMG_WIDTH = 26F * Settings.scale;

		@SpirePrefixPatch
		public static SpireReturn<Float> PrefixChangeHeight(BitmapFont font, String msg, float lineWidth, float lineSpacing) {
			GlyphLayout layout = FontHelper.layout;
			layout.setText(font, msg, Color.WHITE, 0F, -1, false);
			int currentLine = 0;
			float curWidth = 0F;
			float totalHeight = 0F;
			String[] strings = msg.split(" ");
			for (String word : strings) {
				String originalWord = word;
				if (word != null && !word.isEmpty()) {
					if (word.equals("NL")) {
						curWidth = 0F;
						currentLine++;
					} else if (word.equals("TAB")) {
						layout.setText(font, word);
						curWidth += layout.width;
					} else if (word.charAt(0) == '[') {
						if (usingHexColor(word)) {
							int dropStart = word.indexOf("]") + 1;
							int dropEnd = word.lastIndexOf("[");
							word = word.substring(dropStart, dropEnd);
							for (int i = 0; i < word.length(); i++) {
								String j = Character.toString(word.charAt(i));
								layout.setText(font, j);
								curWidth += layout.width;
								if (curWidth > lineWidth) {
									curWidth = layout.width;
									boolean reachEnd = isProbableEndOfColoredMsg(i, j, word, originalWord, strings);
									if (!reachEnd) currentLine++;
								}
							}
						} else if (usingEnergyIcon(word)) {
							boolean hasOrb = identifyOrb(word);
							if (hasOrb) {
								curWidth += CARD_ENERGY_IMG_WIDTH;
								if (curWidth > lineWidth) {
									curWidth = CARD_ENERGY_IMG_WIDTH;
									currentLine++;
								}
							}
						}
					} else if (word.charAt(0) == '#' && usingVanillaColorCode(word)) {
						word = word.substring(2);
						for (int i = 0; i < word.length(); i++) {
							String j = Character.toString(word.charAt(i));
							layout.setText(font, j);
							curWidth += layout.width;
							if (curWidth > lineWidth) {
								curWidth = layout.width;
								boolean reachEnd = isProbableEndOfColoredMsg(i, j, word, originalWord, strings);
								if (!reachEnd) currentLine++;
							}
						}
					} else {
						for (int i = 0; i < word.length(); i++) {
							String j = Character.toString(word.charAt(i));
							layout.setText(font, j);
							curWidth += layout.width;
							if (curWidth > lineWidth) {
								curWidth = layout.width;
								// when current word reaches the end of the text, don't increase currentLine
								boolean reachEnd = isProbableEndOfMsg(i, j, word, strings);
								if (!reachEnd) currentLine++;
							}
						}
					}
				}
			}
			if (currentLine > 0) {
				totalHeight = currentLine * lineSpacing;
			}
			return SpireReturn.Return(totalHeight);
		}
	}
	
	private static boolean isProbableEndOfColoredMsg(int indexOfChar, String singleChar, String modifiedWord, String originalWord,
													 String[] srcMsg) {
		// check if the index of singleChar is the end of the modified word
		// check if singleChar is the same as the last char of the modified word
		// colored text is modified so check if the original word is the last element of the srcMsg
		// instead of the modified word
		return indexOfChar == modifiedWord.length() - 1
				&& Character.toString(modifiedWord.charAt(modifiedWord.length() - 1)).equals(singleChar)
				&& srcMsg[srcMsg.length - 1].equals(originalWord);
	}
	
	private static boolean isProbableEndOfMsg(int indexOfChar, String singleChar, String word, String[] srcMsg) {
		// check if the index of singleChar is the end of the word
		// check if singleChar is the same as the last char of the word
		// check if the word is the last element of the srcMsg
		return indexOfChar == word.length() - 1
				&& Character.toString(word.charAt(word.length() - 1)).equals(singleChar)
				&& srcMsg[srcMsg.length - 1].equals(word);
	}

	public static boolean usingVanillaColorCode(String word) {
		return word.length() > 1 && vanillaColorCode(word.charAt(1));
	}

	private static boolean vanillaColorCode(char s) {
		return s == 'r' || s == 'g' || s == 'b' || s == 'y' || s == 'p';
	}

	private static boolean usingHexColor(String word) {
		// Hex colors have optional alpha bytes
		// [#RRGGBB]
		// [#RRGGBBAA]
		int endOfColor = word.indexOf(']');
		return word.length() > 1 && word.charAt(1) == '#' && (endOfColor == 8 || endOfColor == 10) && word.endsWith("[]");
	}

	private static boolean usingEnergyIcon(String word) {
		return word.length() > 1 && word.charAt(1) == 'E' && word.endsWith("]");
	}

	private static boolean identifyOrb(String word) {
		Object orbTex = ReflectionHacks.privateStaticMethod(FontHelper.class, "identifyOrb", String.class)
				.invoke(new Object[]{ word });
		return orbTex != null;
	}
}
