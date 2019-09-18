import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Scanner;

public class TcpServer {

    public static void main(String[] args) throws InterruptedException {
        TcpServer server = new TcpServer(new NioEventLoopGroup(), new NioEventLoopGroup(), 8080);
        server.start();
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.print("to > ");
            Channel channel = server.channelMap.get(sc.next());
            if (channel == null) {
                logger.error("Server -> no client found");
                break;
            }
            System.out.print("message > ");
            String message = sc.next();
            if (message == null || "".equals(message)) {
                break;
            }
            server.push(message.getBytes(), channel);
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(TcpServer.class);

    private EventLoopGroup boos;
    private EventLoopGroup worker;
    private int port;

    private ChannelGroup channels;
    private HashMap<String, Channel> channelMap;

    public TcpServer(EventLoopGroup boss, EventLoopGroup worker, int port) {
        this.boos = boss;
        this.worker = worker;
        this.port = port;
        this.channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        this.channelMap = new HashMap<String, Channel>();
    }

    public void start() throws InterruptedException {
        ServerBootstrap b = new ServerBootstrap();
        b.group(boos, worker)
         .channel(NioServerSocketChannel.class)
         .option(ChannelOption.SO_BACKLOG, 1024 * 2)
         .childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                 ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 8, 0, 8))
                              .addLast(new LengthFieldPrepender(8))
                              .addLast(new SocketFrameHandler());
             }
        }).bind(port).sync().channel();
    }

    public void stop() {
        boos.shutdownGracefully();
        worker.shutdownGracefully();
    }

    public void push(byte[] msg) {
        channels.writeAndFlush(Unpooled.wrappedBuffer(msg));
    }

    public void push(byte[] msg, Channel channel) {
        channel.writeAndFlush(Unpooled.wrappedBuffer(msg));
    }

    private class SocketFrameHandler extends SimpleChannelInboundHandler<ByteBuf> {

        private String clientId;

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            Channel channel = ctx.channel();
            channels.add(channel);
            clientId = channel.remoteAddress().toString();
            channelMap.put(clientId, channel);
            super.channelRegistered(ctx);
            logger.info("Server -> online {}", clientId);
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
            try {
                int length = msg.readableBytes();
                byte[] code = new byte[length];
                msg.getBytes(msg.readerIndex(), code, 0, length);
                msg.clear();
                logger.info("Server -> message : {}", new String(code));
            } catch (Exception e) {
                logger.error("Server -> channelRead0 {}", e.getMessage());
            }
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
            channels.remove(ctx.channel());
            channelMap.remove(clientId);
            super.channelUnregistered(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }
}