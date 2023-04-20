## Easy Debugger

简单、轻量级桌面端Socket调试工具，基于Netty4.x和JavaFx开发。

## Support

- TCP客户端
- TCP服务端
- UDP客户端
- UDP服务端
- WebSocket服务端
- WebSocket客户端

## TODO

- HTTP服务端
- FTP客户端

## Installing

#### 源码构建

执行 ```./mvnw clean install -DskipTests=true```

#### 打包可执行文件

java8可使用javapackageer

``` 
javapackager -deploy -native image -outdir out -outfile easydebugger -srcfiles easy-debugger-1.0-SNAPSHOT-jar-with-dependencies.jar -appclass io.dengliming.easydebugger.Application -name EasyDebugger
```

## Screenshots

![](https://dengliming.github.io/images/easy-debugger/tcp_server.png)
![](https://dengliming.github.io/images/easy-debugger/tcp_client.png)

## License

[Apache License 2.0](/LICENSE)
