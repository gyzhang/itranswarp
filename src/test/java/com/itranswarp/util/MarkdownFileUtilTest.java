package com.itranswarp.util;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.itranswarp.bean.MarkdownImageBean;

public class MarkdownFileUtilTest {
	@Test
	void testMdFileWebImg() throws Exception {

		String mdFile = "/Users/kevin/temp/test.md";
		String newMdFile = "/Users/kevin/temp/testNew.md";
		String filePath = "/Users/kevin/temp/images/";//测试图片保存地址
		File dir = new File(filePath);//测试图片存储目录不存在就创建
        if (!dir.exists()) {// 判断文件目录是否存在
            dir.mkdirs();
        }
        
		List<String> lines = MarkdownFileUtil.readLines(new File(mdFile));// 读取MD源文件
		Map<Integer, MarkdownImageBean> imgs = MarkdownFileUtil.readImageLines(lines);// 获取MD源文件中的图片标记
		for (MarkdownImageBean img : imgs.values()) {// 下载网络图片
			String url = img.getUrl();
			String type = img.getType();
			String tip = img.getTip();
			String location = img.getLocation();
			File file = new File(filePath + tip + "." + type);
			if ("web".equals(location)) {// 如果是网络图片就下载到本地
				MarkdownFileUtil.writeFile(ImageUtil.readWebImageStream(url, type), file);
			}
		}
		for (int i = 0; i < lines.size(); i++) {// 替换MD文件内容中的图片标签
			if (imgs.containsKey(i)) {
				String localImgUrl = imgs.get(i).getImageMark();
				lines.set(i, localImgUrl);
			}
		}

		MarkdownFileUtil.writeFile(lines, new File(newMdFile));
	}

}
