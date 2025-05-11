package xyz.zzk.deepseek.interfaces;


import xyz.zzk.deepseek.application.IDeepSeekService;
import xyz.zzk.deepseek.application.IWeiXinValidateService;

import xyz.zzk.deepseek.domain.receive.model.MessageTextEntity;
import xyz.zzk.deepseek.infrastructure.util.XmlUtil;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.*;

/**
 * @author 小傅哥，微信：fustack
 * @description 微信公众号，请求处理服务
 * @github https://github.com/fuzhengwei
 * @Copyright 公众号：bugstack虫洞栈 | 博客：https://bugstack.cn - 沉淀、分享、成长，让自己和他人都能有所收获！
 */
@RestController
@RequestMapping("/wx/portal/{appid}")
public class WeiXinPortalController {

    private final Logger logger = LoggerFactory.getLogger(WeiXinPortalController.class);

    @Value("${wx.config.originalid}")
    private String originalId;

    @Resource
    private IWeiXinValidateService weiXinValidateService;

    @Resource
    private IDeepSeekService deepSeekService;

    @Resource
    private ThreadPoolTaskExecutor taskExecutor;

    // 存放OpenAi返回结果数据
    private final Map<String, String> openAiDataMap = new ConcurrentHashMap<>();
    // 存放OpenAi调用次数数据
    private final Map<String, Integer> openAiRetryCountMap = new ConcurrentHashMap<>();

    public WeiXinPortalController() {

    }

    /**
     * 处理微信服务器发来的get请求，进行签名的验证
     * http://xfg-studio.natapp1.cc/wx/portal/wx4bd388e42758df34
     * <p>
     * appid     微信端AppID
     * signature 微信端发来的签名
     * timestamp 微信端发来的时间戳
     * nonce     微信端发来的随机字符串
     * echostr   微信端发来的验证字符串
     */
    @GetMapping(produces = "text/plain;charset=utf-8")
    public String validate(@PathVariable String appid,
                           @RequestParam(value = "signature", required = false) String signature,
                           @RequestParam(value = "timestamp", required = false) String timestamp,
                           @RequestParam(value = "nonce", required = false) String nonce,
                           @RequestParam(value = "echostr", required = false) String echostr) {
        try {
            logger.info("微信公众号验签信息{}开始 [{}, {}, {}, {}]", appid, signature, timestamp, nonce, echostr);
            if (StringUtils.isAnyBlank(signature, timestamp, nonce, echostr)) {
                throw new IllegalArgumentException("请求参数非法，请核实!");
            }
            boolean check = weiXinValidateService.checkSign(signature, timestamp, nonce);
            logger.info("微信公众号验签信息{}完成 check：{}", appid, check);
            if (!check) {
                return null;
            }
            return echostr;
        } catch (Exception e) {
            logger.error("微信公众号验签信息{}失败 [{}, {}, {}, {}]", appid, signature, timestamp, nonce, echostr, e);
            return null;
        }
    }

    /**
     * 此处是处理微信服务器的消息转发的
     */
    @PostMapping(produces = "application/xml; charset=UTF-8")
    public String post(@PathVariable String appid,
                       @RequestBody String requestBody,
                       @RequestParam("signature") String signature,
                       @RequestParam("timestamp") String timestamp,
                       @RequestParam("nonce") String nonce,
                       @RequestParam("openid") String openid,
                       @RequestParam(name = "encrypt_type", required = false) String encType,
                       @RequestParam(name = "msg_signature", required = false) String msgSignature) {
        try {
            logger.info("接收微信公众号信息请求{}开始 {}", openid, requestBody);
            MessageTextEntity message = XmlUtil.xmlToBean(requestBody, MessageTextEntity.class);
            logger.info("请求次数：{}", null == openAiRetryCountMap.get(message.getContent().trim()) ? 1 : openAiRetryCountMap.get(message.getContent().trim()));

            // 异步任务【加入超时重试，对于小体量的调用反馈，可以在重试有效次数内返回结果】
            if (openAiDataMap.get(message.getContent().trim()) == null || "NULL".equals(openAiDataMap.get(message.getContent().trim()))) {
                String data = "消息处理中，请再回复我一句【" + message.getContent().trim() + "】";
                // 休眠等待
                Integer retryCount = openAiRetryCountMap.get(message.getContent().trim());
                if (null == retryCount) {
                    if (openAiDataMap.get(message.getContent().trim()) == null) {
                        doChatGPTTask(message.getContent().trim());
                    }
                    logger.info("超时重试：{}", 1);
                    openAiRetryCountMap.put(message.getContent().trim(), 1);
                    TimeUnit.SECONDS.sleep(5);
                    new CountDownLatch(1).await();
                } else if (retryCount < 2) {
                    retryCount = retryCount + 1;
                    logger.info("超时重试：{}", retryCount);
                    openAiRetryCountMap.put(message.getContent().trim(), retryCount);
                    TimeUnit.SECONDS.sleep(5);
                    new CountDownLatch(1).await();
                } else {
                    retryCount = retryCount + 1;
                    logger.info("超时重试：{}", retryCount);
                    openAiRetryCountMap.put(message.getContent().trim(), retryCount);
                    TimeUnit.SECONDS.sleep(3);
                    if (openAiDataMap.get(message.getContent().trim()) != null && !"NULL".equals(openAiDataMap.get(message.getContent().trim()))) {
                        data = openAiDataMap.get(message.getContent().trim());
                    }
                }

                // 反馈信息[文本]
                MessageTextEntity res = new MessageTextEntity();
                res.setToUserName(openid);
                res.setFromUserName(originalId);
                res.setCreateTime(String.valueOf(System.currentTimeMillis() / 1000L));
                res.setMsgType("text");
                res.setContent(data);

                return XmlUtil.beanToXml(res);
            }

            // 反馈信息[文本]
            MessageTextEntity res = new MessageTextEntity();
            res.setToUserName(openid);
            res.setFromUserName(originalId);
            res.setCreateTime(String.valueOf(System.currentTimeMillis() / 1000L));
            res.setMsgType("text");
            res.setContent(openAiDataMap.get(message.getContent().trim()));
            String result = XmlUtil.beanToXml(res);
            logger.info("接收微信公众号信息请求{}完成 {}", openid, result);
            openAiDataMap.remove(message.getContent().trim());
            return result;
        } catch (Exception e) {
            logger.error("接收微信公众号信息请求{}失败 {}", openid, requestBody, e);
            return "";
        }
    }

    public void doChatGPTTask(String content) {
        openAiDataMap.put(content, "NULL");
        taskExecutor.execute(() -> {
            try {
                String response = deepSeekService.chat(content);
                openAiDataMap.put(content, response);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        });
    }

}
