import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TcpClient {

    public static void main(String[] args) {
        TcpClient client = new TcpClient(new NioEventLoopGroup(), "127.0.0.1", 8080);
        client.start();
    }

    private static final Logger logger = LoggerFactory.getLogger(TcpClient.class);

    private EventLoopGroup boos;
    private String ip;
    private int port;

    private Bootstrap boot;

    private Channel channel;

    public TcpClient(EventLoopGroup boss, String ip, int port) {
        this.boos = boss;
        this.ip   = ip;
        this.port = port;
        this.boot = new Bootstrap();
    }

    public void start() {
        try {
            channel = boot.group(boos)
                          .remoteAddress(ip, port)
                          .channel(NioSocketChannel.class)
                          .option(ChannelOption.TCP_NODELAY, true)
                          .handler(new ChannelInitializer<SocketChannel>() {
                                @Override
                                protected void initChannel(SocketChannel ch) {
                                  ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 8, 0, 8))
                                               .addLast(new LengthFieldPrepender(8))
                                               .addLast(new SocketFrameHandler());
                              }
                          }).connect().sync().channel();

        } catch (Exception e) {
            logger.error("Client -> start {}", e.getMessage());
        }
    }

    public void stop() {
        channel.close();
        boos.shutdownGracefully();
    }

    public void submission(byte[] msg) {
        channel.writeAndFlush(Unpooled.wrappedBuffer(msg));
    }

    private class SocketFrameHandler extends SimpleChannelInboundHandler<ByteBuf> {
        @Override
        public void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
            try {
                int length  = msg.readableBytes();
                byte[] code = new byte[length];
                msg.getBytes(msg.readerIndex(), code, 0, length);
                msg.clear();

                logger.info("Client -> message : {}", new String(code));
            } catch (Exception e) {
                logger.error("Client -> channelRead0 {}", e.getMessage());
            }
        }
    }
}