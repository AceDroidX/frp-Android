sudo sed -i 's/deb.debian.org/mirrors.ustc.edu.cn/g' /etc/apt/sources.list.d/debian.sources
sudo apt update
sudo apt install npm -y
curl -fsSL https://opencode.ai/install | bash
sudo chown -R vscode:vscode /home/vscode/.cache