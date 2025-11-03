# JamnWebContentProvider

This is a sample implementation of a simple file-based <a href="/org.isa.ipc.JamnWebContentProvider/src/main/java/org/isa/ipc/JamnWebContentProvider.java">Web Content Provider</a>.

That may sound like at first - but it is NOT about reinventing the web server wheel.

The approach is to reduce the server requirements to basic web like networking capability and consider everything else as part of an individual application.

Normally, you have to build a web-enabled application so that it can be installed on a WebServer and you need the WebServer itself as an infrastructure component.
Jamn turns things around and makes every Java Application a provider of a web interface that is part and under control of the Application itself.
