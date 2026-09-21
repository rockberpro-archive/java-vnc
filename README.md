# VNC simples em Java 25

Compartilha a tela de um computador e permite controlá-lo (mouse + teclado) por TCP.

- `Server.java`: máquina controlada: captura a tela, divide em quadros de 64x64 e envia
  **somente os quadros que mudaram** (PNG por quadro). Recebe eventos de mouse/teclado e
  executa via `java.awt.Robot`.
- `Viewer.java`: máquina controladora: monta a imagem a partir dos quadros e envia mouse/teclado.

## Executar

```
javac *.java
java Server            # porta 5900 (ou: java Server 6000)
java Viewer <ip>       # ou: java Viewer <ip> 6000
```
