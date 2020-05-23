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

import com.itranswarp.bean.MarkdownImageBean;

/**
 * 操作MarkDown文件的工具类
 * 
 * @author kevin
 *
 */
public class MarkdownFileUtil {

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
				lines.add(line);
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
		String[] strs = content.split(System.getProperty("line.separator"));
		List<String> lines = Arrays.asList(strs);
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
	 * 将按行存储的list写入文件
	 * 
	 * @param lines
	 * @param file
	 * @throws IOException
	 */
	public static void writeFile(List<String> lines, File file) throws IOException {
		try (FileWriter fw = new FileWriter(file)) {
			for (int i = 0; i < lines.size(); i++) {
				fw.write(lines.get(i));
				fw.write(System.getProperty("line.separator"));
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
