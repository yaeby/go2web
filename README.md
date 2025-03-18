# Go2Web - Java CLI Web Client 🌐

A lightweight Java command-line tool for making web requests with caching, content parsing, and search capabilities.

📽️ Watch demo video:  
[**Demo Video Link**](https://youtu.be/sOWAk9_i2ro)

## Features ✨
- **Smart Caching**: Automatic cache management with TTL-based expiration
- **Content Parsing**: Clean HTML-to-text conversion & JSON formatting
- **Search Integration**: DuckDuckGo search with interactive results
- **Redirect Handling**: Automatic redirect following (max 5 hops)
- **Cache Persistence**: Survives restarts through serialization
- **HTTP/1.1 Compliance**: Proper headers and connection handling

## Installation ⚙️

**Requirements**: Java JDK 23+

```bash
git clone https://github.com/yourusername/go2web.git
cd go2web
mvn clean package
```

## Cross-Platform Setup 🖥️

### Windows Execution

After the installation steps on Windows, you can run the program using `./go2web` (a `.bat` is already implemented for you)  
Or if u want the `go2web.bat` callable from anywhere, you can follow the steps:

1. Place your `go2web.bat` file in a directory like `C:\Program Files\Go2Web\` and change its content to:
```batch
@echo off
set SCRIPT_DIR=%~dp0
java -jar "%SCRIPT_DIR%go2web-1.0-SNAPSHOT.jar" %*
```

2. Add the `go2web-1.0-SNAPSHOT.jar` to `C:\Program Files\Go2Web\`.
```bash
mv go2web-1.0-SNAPSHOT.jar "C:\Program Files\Go2Web\"
```

3. Add `C:\Program Files\Go2Web\` to the System PATH
```powershell
[Environment]::SetEnvironmentVariable(
    "Path",
    [Environment]::GetEnvironmentVariable("Path", [EnvironmentVariableTarget]::Machine) + ";C:\Program Files\Go2Web",
    [EnvironmentVariableTarget]::Machine
)
```
or using: cmd -> sysdm.cpl -> Advanced -> Environment Variables -> Path -> add (C:\Program Files\Go2Web\)

4. Verify in new terminal:
```cmd
go2web -h
```

### Linux Execution
1. Create system-wide script:
```bash
sudo nano /usr/local/bin/go2web
```

2. Add this content:
```bash
#!/bin/bash
java -jar /path/to/your/go2web-1.0-SNAPSHOT.jar "$@"
```

3. Make executable:
```bash
sudo chmod +x /usr/local/bin/go2web
```

4. Verify installation:
```bash
go2web -u https://example.com
```

### PATH Configuration Diagram
```
System PATH Locations:
├── Windows: 
│   └── C:\Program Files\Go2Web\go2web.bat
└── Linux:
    └── /usr/local/bin/go2web (shell script)
        └── Linked to your installation directory
```

## Usage 

```bash
go2web -u <URL>         # make an HTTP request to the specified URL and print the response
go2web -s <search-term> # make an HTTP request to search the term using your favorite search engine and print top 10 results
go2web -h               # show this help

```

## Caching Mechanism 💾
- Stores responses in `go2web_cache.dat`
- Respects `Cache-Control` and `Expires` headers
- Default TTL: 1 hour for responses without cache headers
- Automatic cache pruning on startup
