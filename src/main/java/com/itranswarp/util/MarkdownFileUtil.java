package com.itranswarp.util;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.itranswarp.bean.MarkdownImageBean;

/**
 * 操作MarkDown文件的工具类
 *
 * @author kevin
 */
public class MarkdownFileUtil {

	/**
	 * 添加一个非空行
	 *
	 * @param lines 接受行的list
	 * @param line  当前非空行，有可能含有内置夹杂图片标签
	 */
	private static void addLine(List<String> lines, String line) {
		if (line.equals("")) {//空行
			lines.add(line);
		} else {//非空行
			List<String> tempLines = covertLines(line);//将1行文字拆分，找出其中夹杂的图片标签
			if (tempLines.size() > 1) {//行内夹杂图片，拆分后才会是多行
				for (String tempLine: tempLines) {
					if (!tempLine.trim().equals("")) {//不是只包含空格的空行，也就是有文字内容的行。不含文字的空行不处理，丢弃
						lines.add(tempLine);
						lines.add("");
					}
				}
			} else {//只有1行，没有夹杂图片标签，直接输出
				lines.add(line);
			}
		}
	}

	/**
	 * 按行读取文件到List中
	 *
	 * @param file 待读取的文件
	 * @return 按行存储的list
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static List<String> readLines(File file) throws FileNotFoundException, IOException {
		try (BufferedReader bf = new BufferedReader(new FileReader(file))) {
			List<String> lines = new ArrayList<String>();
			String line;
			// 按行读取字符串
			while ((line = bf.readLine()) != null) {
				addLine(lines, line);
			}
			return lines;
		}
	}

	/**
	 * 从文本中按行划分为List
	 *
	 * @param content
	 * @return
	 */
	public static List<String> readLines(String content) {
		String[] contentLines = content.split(System.getProperty("line.separator"));
		List<String> lines = new ArrayList<String>();

		for (String line : contentLines) {
			addLine(lines, line);
		}

		return lines;
	}

	/**
	 * 将按行存储的list写入文件
	 *
	 * @param lines
	 * @param file
	 * @throws IOException
	 */
	public static void writeFile(List<String> lines, File file) throws IOException {
		String separator = System.getProperty("line.separator");
		try (FileWriter fw = new FileWriter(file)) {
			for (String line : lines) {
				fw.write(line);
				fw.write(separator);//回车换行
			}
		}
	}

	/**
	 * 将字节数组写入文件
	 *
	 * @param bytes
	 * @param file
	 * @throws IOException
	 */
	public static void writeFile(byte[] bytes, File file) throws IOException {
		try (FileOutputStream fos = new FileOutputStream(file);
			 BufferedOutputStream bos = new BufferedOutputStream(fos)) {
			bos.write(bytes);
		}
	}

	/**
	 * 将1行文字中含有图片的内容拆分成多行。
	 * 例如：下面这张图片在md文件中没有换行。![微信支付](images/wechatPay.png)这张图片夹杂![支付宝支付](images/AliPay.png)在一行文字中。
	 * 上面这行文字将拆分为5行
	 *
	 * @param line 文字内容
	 * @return 拆分为多行的一个list
	 */
	public static List<String> covertLines(String line) {
		List<String> lines = new ArrayList<String>();

		String imgRegex = "!\\[(.*?)\\]\\((.*?)\\)";//MarkDown文件中的图片正则表达式
		Pattern patten = Pattern.compile(imgRegex);
		Matcher matcher = patten.matcher(line);
		List<String> imgStrs = new ArrayList<String>();
		while (matcher.find()) {//find每次被调用后，会偏移到下一个匹配
			imgStrs.add(matcher.group());//获取当前匹配的值
		}

		int start = 0;
		for (String imgStr : imgStrs) {
			if (line.indexOf(imgStr) > 0) {//图片不在开头
				lines.add(line.substring(start, line.indexOf(imgStr)));//图片前面的文字
			}
			lines.add(imgStr);//添加图片标签行
			start = line.indexOf(imgStr) + imgStr.length();
		}
		if (start < line.length()) {//最后一个图片标签后面的文字
			lines.add(line.substring(start));
		}

		return lines;
	}

	/**
	 * 获取图片标记行
	 *
	 * @param lines MD文件的所有行
	 * @return MD文件中的图片标记行
	 */
	public static Map<Integer, MarkdownImageBean> readImageLines(List<String> lines) {
		Map<Integer, MarkdownImageBean> imgs = new HashMap<Integer, MarkdownImageBean>();
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i).trim();
			if (line.startsWith("![") && line.endsWith(")")) {// MarkDown 图片标签为 ![这是一张图片](http://www.abc.com/def.png)
				String imgUrl = line.substring(line.indexOf("](") + 2, line.lastIndexOf(")"));// 提取图片url地址
				String imgTip = line.substring(line.indexOf("![") + 2, line.indexOf("]"));// 提取图片的描述信息
				String imgType = "jpeg";// 提取图片的类型
				if (line.indexOf(".png") != -1) {
					imgType = "png";
				} else if (line.indexOf(".jpg") != -1) {
					imgType = "jpeg";
				} else if (line.indexOf(".gif") != -1) {
					imgType = "gif";
				} else if (line.indexOf(".bpm") != -1) {
					imgType = "bmp";
				}
				// 判断图片是否是web图片
				String location = "local"; // 默认本地图片
				if (line.indexOf("http://") != -1 || line.indexOf("https://") != -1) {
					location = "web";
				}
				String imageMark = "![" + imgTip + "](" + "images/" + imgTip + "." + imgType + ")";// 保存到当前文件的imges目录下
				MarkdownImageBean imgBean = new MarkdownImageBean();// 创建这张图片的Map存储对象
				imgBean.setLine(i);
				imgBean.setUrl(imgUrl);
				imgBean.setTip(imgTip);
				imgBean.setType(imgType);
				imgBean.setLocation(location);// 图片位置：本地local｜网络web
				imgBean.setImageMark(imageMark);// 更新过的图片标签

				imgs.put(i, imgBean);
			}
		}
		return imgs;
	}

	/**
	 * 去除左空格
	 *
	 * @param str
	 * @return
	 */
	public static String trimLeft(String str) {
		if (str == null || str.equals("")) {
			return str;
		} else {
			return str.replaceAll("^[　 ]+", "");
		}
	}

	/**
	 * 去除右空格
	 *
	 * @param str
	 * @return
	 */
	public static String trimRight(String str) {
		if (str == null || str.equals("")) {
			return str;
		} else {
			return str.replaceAll("[　 ]+$", "");
		}
	}

}
