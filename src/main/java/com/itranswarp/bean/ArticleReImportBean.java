package com.itranswarp.bean;

/**
 * 重新导入文章用的封装bean
 */
public class ArticleReImportBean {
    private long articleId;//当前文章的id
    private String articlePath;//重新导入（更新）的文章在服务器上的绝对路径

    public long getArticleId() {
        return articleId;
    }

    public void setArticleId(long articleId) {
        this.articleId = articleId;
    }

    public String getArticlePath() {
        return articlePath;
    }

    public void setArticlePath(String articlePath) {
        this.articlePath = articlePath;
    }
}
