package com.itranswarp.util;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Iterator;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import com.itranswarp.bean.ImageBean;
import com.itranswarp.common.ApiException;
import com.itranswarp.enums.ApiError;

/**
 * Util for creating thumbnails.
 * 
 * @author liaoxuefeng
 */
public class ImageUtil {

	public static ImageBean readImage(byte[] data) {
		BufferedImage image;
		String format;
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (readers.hasNext()) {
				ImageReader reader = null;
				try {
					reader = readers.next();
					reader.setInput(input);
					format = reader.getFormatName();
					if ("gif".equals(format)) {//解决jdk的gif读取错误：Index 4096 out of bounds for length 4096
						image = GifDecoder.read(data).getFrame(0);
					} else {
						image = reader.read(0);
					}
					
					if (readers.hasNext()) {
						throw new ApiException(ApiError.PARAMETER_INVALID, "data", "Invalid data.");
					}
				} finally {
					if (reader != null) {
						reader.dispose();
					}
				}
			} else {
				throw new ApiException(ApiError.PARAMETER_INVALID, "image", "Invalid data.");
			}
		} catch (IOException e) {
			throw new ApiException(ApiError.PARAMETER_INVALID, "image", "Invalid data.");
		}
		ImageBean bean = new ImageBean();
		bean.image = image;
		bean.width = image.getWidth();
		bean.height = image.getHeight();
		bean.size = data.length;
		bean.mime = "image/" + checkFormat(format);
		return bean;
	}

	static String checkFormat(String format) {
		if (format == null) {
			throw new ApiException(ApiError.PARAMETER_INVALID, "image", "Unsupported image type.");
		}
		format = format.toLowerCase();
		if (!SUPPORTED_FORMAT.contains(format)) {
			throw new ApiException(ApiError.PARAMETER_INVALID, "image", "Unsupported image type.");
		}
		return format;
	}

	static final Set<String> SUPPORTED_FORMAT = Set.of("jpeg", "png", "gif");

	public static BufferedImage resizeKeepRatio(BufferedImage input, int expectedWidth) {
		int originalWidth = input.getWidth();
		int originalHeight = input.getHeight();
		if (originalWidth <= expectedWidth) {
			return input;
		}
		int expectedHeight = originalHeight * expectedWidth / originalWidth;
		if (expectedHeight < 10) {
			expectedHeight = 10;
		}
		BufferedImage output = new BufferedImage(expectedWidth, expectedHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2 = output.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2.drawImage(input, 0, 0, expectedWidth, expectedHeight, null);
		g2.dispose();
		return output;
	}

	public static String encodeJpegAsBase64(BufferedImage image) throws IOException {
		try (var out = new ByteArrayOutputStream()) {
			ImageIO.write(image, "jpeg", out);
			return Base64.getEncoder().encodeToString(out.toByteArray());
		}
	}
	
	/**
	 * 从网络链接获取图片
	 * @param imgUrl 网络图片地址
	 * @param imgType 图片类型：jpeg｜bmp｜gif｜png
	 * @return 存放图片的字节数组
	 * @throws Exception
	 */
	public static byte[] readWebImageStream(String imgUrl, String imgType) throws Exception {
		byte[] bitImg;
		
		CloseableHttpClient httpClient = HttpClients.createDefault();
		CloseableHttpResponse response = null;
		HttpGet httpGet = new HttpGet(imgUrl);
		// 浏览器表示
		httpGet.addHeader("User-Agent", "Mozilla/5.0 (Windows; U; Windows NT 5.1; en-US; rv:1.7.6)");
		// 传输的类型
		httpGet.addHeader("Content-Type", "image/" + imgType.toLowerCase());//有效类型为：image/jpeg image/bmp image/gif image/png
		try {
			// 执行请求
			response = httpClient.execute(httpGet);
			// 获得响应的实体对象
			HttpEntity entity = response.getEntity();
			// 包装成高效流
			BufferedInputStream bis = new BufferedInputStream(entity.getContent());
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			byte[] byt = new byte[1024 * 8];
			Integer len = -1;
			while ((len = bis.read(byt)) != -1) {
				bos.write(byt, 0, len);
			}
			bitImg = bos.toByteArray();
			
			bos.close();
			bis.close();
		} finally {
			// 释放连接
			if (null != response) {
				try {
					response.close();
					httpClient.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		return bitImg;
	}	
	
	/**
	 * 从本地文件中读取到字节数组
	 * @param fileName 文件路径
	 * @return
	 * @throws Exception
	 */
	public static byte[] readLocalImageStream(String fileName) throws Exception {
		byte[] bitImg;
		try (InputStream in = new FileInputStream(fileName)) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[1024 * 8];
			int n = 0;
			while ((n = in.read(buffer)) != -1) {
			    out.write(buffer, 0, n);
			}
			bitImg = out.toByteArray();
		}
		return bitImg;
	}
}
