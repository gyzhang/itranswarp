package com.itranswarp.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.itranswarp.bean.AttachmentBean;
import com.itranswarp.bean.DownloadBean;
import com.itranswarp.bean.ImageBean;
import com.itranswarp.common.ApiException;
import com.itranswarp.enums.ApiError;
import com.itranswarp.enums.ResourceEncoding;
import com.itranswarp.enums.Role;
import com.itranswarp.model.Attachment;
import com.itranswarp.model.Resource;
import com.itranswarp.model.User;
import com.itranswarp.util.HashUtil;
import com.itranswarp.util.IdUtil;
import com.itranswarp.util.ImageUtil;
import com.itranswarp.warpdb.PagedResults;

@Component
public class AttachmentService extends AbstractService<Attachment> {

	public PagedResults<Attachment> getAttachments(int pageIndex) {
		return this.db.from(Attachment.class).orderBy("createdAt").desc().list(pageIndex, ITEMS_PER_PAGE);
	}

	@Transactional
	public Attachment createAttachment(User user, AttachmentBean bean) {
		bean.validate(true);
		bean.name = bean.name;
		byte[] data = Base64.getDecoder().decode(bean.data);
		ImageBean image = ImageUtil.readImage(data);

		String hash = HashUtil.sha256(bean.data);
		Resource r = this.db.from(Resource.class).where("hash = ?", hash).first();
		if (r == null) {
			r = new Resource();
			r.id = IdUtil.nextId();
			r.encoding = ResourceEncoding.BASE64;
			r.hash = hash;
			r.content = bean.data;
			this.db.insert(r);
		}

		Attachment a = new Attachment();
		a.resourceId = r.id;
		a.userId = user.id;
		a.name = bean.name;
		a.mime = image.mime;
		a.size = data.length;
		a.width = image.width;
		a.height = image.height;
		this.db.insert(a);
		return a;
	}

	/**
	 * 通过url导入网络图片创建附件
	 * @param user 当前登录用户
	 * @param url 图片的网络地址
	 * @param type 图片类型
	 * @param name 存入系统附件的名称
	 * @return
	 * @throws Exception
	 */
	@Transactional
	public Attachment importWebAttachment(User user, String url, String type, String name) throws Exception {
		AttachmentBean bean = new AttachmentBean();
		byte[] img = ImageUtil.readWebImageStream(url, type);
		bean.data = Base64.getEncoder().encodeToString(img);
		bean.name = name;
		return createAttachment(user, bean);
	}
	
	/**
	 * 导入本地文件（图片）创建附件
	 * @param user 当前登录用户
	 * @param url 服务器上文件绝对路径
	 * @param type 图片类型
	 * @param name 存入系统附件的名称
	 * @return
	 * @throws Exception
	 */
	@Transactional
	public Attachment importLocalAttachment(User user, String url, String name) throws Exception {
		AttachmentBean bean = new AttachmentBean();
		byte[] img = ImageUtil.readLocalImageStream(url);
		bean.data = Base64.getEncoder().encodeToString(img);
		bean.name = name;
		return createAttachment(user, bean);
	}

	@Transactional
	public void deleteAttachment(User user, Long id) {
		Attachment a = this.getById(id);
		if (user.role != Role.ADMIN && user.id != a.userId) {
			throw new ApiException(ApiError.PERMISSION_DENIED);
		}
		long resourceId = a.resourceId;
		this.db.remove(a);
		if (this.db.from(Attachment.class).where("resourceId = ?", resourceId).first() == null) {
			Resource resource = new Resource();
			resource.id = resourceId;
			this.db.remove(resource);
		}
	}

	public DownloadBean downloadAttachment(Long id, char size) {
		if ("0sml".indexOf(size) == (-1)) {
			throw new ApiException(ApiError.PARAMETER_INVALID, "size", "Invalid size.");
		}
		Attachment a = this.getById(id);
		Resource r = this.db.fetch(Resource.class, a.resourceId);
		if (r == null) {
			throw new ApiException(ApiError.PARAMETER_INVALID, "id", "Resource not found.");
		}
		if (size == '0') {
			return new DownloadBean(a.mime, r.decode());
		}
		int originWidth = a.width;
		int targetWidth = originWidth;
		boolean resize = false;
		if (size == 's') {
			if (originWidth > 160) {
				targetWidth = 160;
				resize = true;
			}
		} else if (size == 'm') {
			if (originWidth > 320) {
				targetWidth = 320;
				resize = true;
			}
		} else if (size == 'l') {
			if (originWidth > 640) {
				targetWidth = 640;
				resize = true;
			}
		}
		if (!resize) {
			return new DownloadBean(a.mime, r.decode());
		}
		BufferedImage resizedImage = null;
		try (var input = new ByteArrayInputStream(r.decode())) {
			BufferedImage originImage = ImageIO.read(input);
			resizedImage = ImageUtil.resizeKeepRatio(originImage, targetWidth);
		} catch (IOException e) {
			throw new ApiException(ApiError.OPERATION_FAILED, null, "Could not resize image.");
		}
		try (var output = new ByteArrayOutputStream()) {
			ImageIO.write(resizedImage, "jpeg", output);
			return new DownloadBean("image/jpeg", output.toByteArray());
		} catch (IOException e) {
			throw new ApiException(ApiError.OPERATION_FAILED, null, "Could not resize image.");
		}
	}

	String checkMime(String mime) {
		if (mime == null || mime.isEmpty()) {
			throw new ApiException(ApiError.PARAMETER_INVALID, "mime", "Invalid mime type.");
		}
		int n = mime.indexOf(';');
		if (n >= 0) {
			mime = mime.substring(0, n);
		}
		mime = mime.toLowerCase();
		if (!SUPPORTED_MIME_TYPES.contains(mime)) {
			throw new ApiException(ApiError.PARAMETER_INVALID, "mime", "Unsupported mime type.");
		}
		return mime;
	}

	static final Set<String> SUPPORTED_MIME_TYPES = Set.copyOf(List.of("image/jpeg", "image/gif", "image/png"));
}
