package com.ld.poetry.im.websocket;

import com.ld.poetry.constants.CommonConst;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tio.core.ChannelContext;
import org.tio.core.Tio;
import org.tio.core.intf.Packet;
import org.tio.websocket.common.WsResponse;
import org.tio.websocket.server.WsServerAioListener;
import com.ld.poetry.im.websocket.TioWebsocketStarter;
import com.ld.poetry.im.websocket.TioUtil;
import org.tio.utils.lock.SetWithLock;

import java.util.Set;

@Component
@Slf4j
public class ImServerAioListener extends WsServerAioListener {

    /**
     * 建链后触发本方法，注：建链不一定成功，需要关注参数isConnected
     *
     * @param channelContext
     * @param isConnected    是否连接成功,true:表示连接成功，false:表示连接失败
     * @param isReconnect    是否是重连,true:表示这是重新连接，false:表示这是第一次连接
     */
    @Override
    public void onAfterConnected(ChannelContext channelContext, boolean isConnected, boolean isReconnect) throws Exception {
        super.onAfterConnected(channelContext, isConnected, isReconnect);
        if (log.isInfoEnabled()) {
            log.info("onAfterConnected\r\n{}", channelContext);
        }
        
        // 注意：不在这里广播在线用户数，因为此时还没有绑定群组
        // 在线用户数会在 onAfterHandshaked 完成后延迟广播（等待旧连接关闭）
    }

    /**
     * 解码成功后触发本方法
     */
    @Override
    public void onAfterDecoded(ChannelContext channelContext, Packet packet, int packetSize) throws Exception {
        super.onAfterDecoded(channelContext, packet, packetSize);
    }

    /**
     * 消息包发送之后触发本方法
     *
     * @param channelContext
     * @param packet
     * @param isSentSuccess  true:发送成功，false:发送失败
     */
    @Override
    public void onAfterSent(ChannelContext channelContext, Packet packet, boolean isSentSuccess) throws Exception {
        super.onAfterSent(channelContext, packet, isSentSuccess);
    }

    /**
     * 连接关闭前触发本方法
     */
    @Override
    public void onBeforeClose(ChannelContext channelContext, Throwable throwable, String remark, boolean isRemove) throws Exception {
        if (log.isInfoEnabled()) {
            log.info("onBeforeClose\r\n{}", channelContext);
        }
        
        // 用户断开连接前，推送在线用户数更新（需要在super调用前处理，此时用户还在群组中）
        broadcastOnlineCountUpdateOnLeave(channelContext);
        
        super.onBeforeClose(channelContext, throwable, remark, isRemove);
    }

    /**
     * 处理一个消息包后
     *
     * @param channelContext
     * @param packet
     * @param cost           本次处理消息耗时，单位：毫秒
     */
    @Override
    public void onAfterHandled(ChannelContext channelContext, Packet packet, long cost) throws Exception {
        super.onAfterHandled(channelContext, packet, cost);
    }

    /**
     * 接收到TCP层传过来的数据后
     *
     * @param channelContext
     * @param receivedBytes  本次接收了多少字节
     */
    @Override
    public void onAfterReceivedBytes(ChannelContext channelContext, int receivedBytes) throws Exception {
        super.onAfterReceivedBytes(channelContext, receivedBytes);
    }

    /**
     * 服务器检查到心跳超时时，会调用这个函数（一般场景，该方法只需要直接返回false即可）
     *
     * @param channelContext
     * @param interval              已经多久没有收发消息了，单位：毫秒
     * @param heartbeatTimeoutCount 心跳超时次数，第一次超时此值是1，以此类推。此值被保存在：channelContext.stat.heartbeatTimeoutCount
     * @return 返回true，那么服务器则不关闭此连接；返回false，服务器将按心跳超时关闭该连接
     */
    @Override
    public boolean onHeartbeatTimeout(ChannelContext channelContext, Long interval, int heartbeatTimeoutCount) {
        return super.onHeartbeatTimeout(channelContext, interval, heartbeatTimeoutCount);
    }

    /**
     * 广播在线用户数更新
     */
    private void broadcastOnlineCountUpdate(ChannelContext channelContext) {
        try {
            // 获取用户所在的群组
            SetWithLock<String> groups = channelContext.getGroups();
            if (groups != null && groups.size() > 0) {
                TioWebsocketStarter tioWebsocketStarter = TioUtil.getTio();
                if (tioWebsocketStarter == null) {
                    return;
                }
                
                for (String groupId : groups.getObj()) {
                    // 获取群组在线用户数
                    int onlineCount = Tio.getByGroup(tioWebsocketStarter.getServerTioConfig(), groupId).size();
                    
                    // 构建在线用户数消息
                    ImMessage imMessage = new ImMessage();
                    imMessage.setMessageType(CommonConst.ONLINE_COUNT_MESSAGE_TYPE);
                    imMessage.setOnlineCount(onlineCount);
                    imMessage.setGroupId(Integer.valueOf(groupId));
                    
                    // 向群组广播在线用户数
                    WsResponse wsResponse = WsResponse.fromText(imMessage.toJsonString(), CommonConst.CHARSET_NAME);
                    Tio.sendToGroup(tioWebsocketStarter.getServerTioConfig(), groupId, wsResponse);
                    
                    if (log.isDebugEnabled()) {
                        log.debug("广播群组{}在线用户数更新: {}", groupId, onlineCount);
                    }
                }
            }
        } catch (Exception e) {
            log.error("广播在线用户数更新失败", e);
        }
    }

    /**
     * 用户离开时广播在线用户数更新（需要减1）
     */
    private void broadcastOnlineCountUpdateOnLeave(ChannelContext channelContext) {
        try {
            // 获取用户所在的群组
            SetWithLock<String> groups = channelContext.getGroups();
            if (groups != null && groups.size() > 0) {
                TioWebsocketStarter tioWebsocketStarter = TioUtil.getTio();
                if (tioWebsocketStarter == null) {
                    return;
                }
                
                for (String groupId : groups.getObj()) {
                    // 获取群组在线用户数（当前用户还在群组中，所以需要减1）
                    int currentCount = Tio.getByGroup(tioWebsocketStarter.getServerTioConfig(), groupId).size();
                    int onlineCount = Math.max(0, currentCount - 1);
                    
                    // 构建在线用户数消息
                    ImMessage imMessage = new ImMessage();
                    imMessage.setMessageType(CommonConst.ONLINE_COUNT_MESSAGE_TYPE);
                    imMessage.setOnlineCount(onlineCount);
                    imMessage.setGroupId(Integer.valueOf(groupId));
                    
                    // 向群组广播在线用户数（排除当前即将离开的用户）
                    WsResponse wsResponse = WsResponse.fromText(imMessage.toJsonString(), CommonConst.CHARSET_NAME);
                    Tio.sendToGroup(tioWebsocketStarter.getServerTioConfig(), groupId, wsResponse);
                    
                    if (log.isDebugEnabled()) {
                        log.debug("广播群组{}用户离开后在线用户数: {} -> {}", groupId, currentCount, onlineCount);
                    }
                }
            }
        } catch (Exception e) {
            log.error("广播用户离开时在线用户数更新失败", e);
        }
    }
}
