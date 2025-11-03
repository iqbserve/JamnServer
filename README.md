# Jamn - Just Another Micro Node Server

Jamn is an **experimental**, lightweight **Java MicroServer** (less than 200 KB) for textdata driven application communication with e.g. JSON/XML/HTML etc. based on a rudimentary HTTP compatibility.

The Motivation and Goal of Jamn is to provide a simple and independent way for ...
* building local and remote "All in One" Rich Web GUI Apps 
* having a universal, network-capable Java-Bridge 
* complete control over Backend and Frontend in "One App" without infrastructure components and tools

The implementation is designed for easy manageability and high degree of customizability. It uses only Standard Java-SE functionality and the server is <a href="/org.isa.ipc.JamnServer/src/main/java/org/isa/ipc/JamnServer.java">implemented in ONE tiny class file.</a> With the exception of the optional Json and JavaScript-Engine there are NO dependencies to any APIs or Libraries.

The basic design is layered and modular - so users can easily adapt anything to their own needs
* Server kernel - socket and request handling for basic http protocol
* Provider modules for:
  - Web Content - html, js, image etc. content and files
  - Web Services - functionality as REST like apis
  - Web Socket - bidirectional Front-/Backend communication
  - JavaScript - extending/implementing functionality

**NOTES**:
1. The term "node" in the name just means "node" - NOT Node.js. **Jamn does NOT use Node.js.**
<br></br>
2. Although the current server supports http in a basic form, **it is NOT intended to be a fully featured HTTP/Web Server and it is NOT suitable for such production purposes**. But it is quick and easy to use e.g. for tooling, testing or concept experiments  - cause no infrastructure and no external components are required. <a href="/org.isa.ipc.JamnServer/src/test/java/org/isa/ipc/JamnServerBasicTest.java"> (basic usage example)</a>
<br><br>
# Jamn Personal Server-App
The <a href="/org.isa.jps.JamnPersonalServerApp">Personal App</a> exemplary assembles the Jamn Components in one application scaffolding (< 200 KB) extending the combination with a folder structure, a JavaScript Backend integration and a WebUI base for Browser based Frontends. It is a sample and a playground for possible use cases.

Individual user functions and services can be implemented on the Backend-Side in Java, Java-Script or as Shell-Scripts ([Extensions](https://github.com/integrating-architecture/JamnServer/tree/master/org.isa.jps.JamnPersonalServerApp/extensions))<br>
and on the Frontend-Side in HTML/CSS and Java-Script ([RIA](https://github.com/integrating-architecture/JamnServer/tree/master/org.isa.jps.JamnPersonalServerApp/http)), as required.

The App and the UI can be used local and/or remote - without changes and without having to meet any specifications or any other restrictions.<br>
There is No deployment - No dependency management - No external tooling required.  

Workbench App <a href="https://wbapp.iqbserve.de" target="_blank">Demo</a>
<br>
<img src="https://github.com/user-attachments/assets/e2fe7d5b-015f-467d-b1dc-c9e6f37d35fd" width="600" height="400"></br>   

<br></br>
## Disclamer  
Affects all source and binary code from

	
Use and redistribution in source and binary forms,
with or without modification, are permitted WITHOUT restriction of any kind.  

THIS SOFTWARE IS PROVIDED BY THE AUTHORS, COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, 
THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR 
PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE AUTHOR, COPYRIGHT HOLDER OR CONTRIBUTOR
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL 
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER 
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, 
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE 
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
