#!/bin/bash
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# WARNING: Please double-check with the documentation in node-admin/README*
# whether these commands are in fact correct. If they are, this saves a bunch
# of typing...
#
# See HelpAndExit below for usage.

set -ex

declare DAYS_VALID=3650

# Note regarding the file names: Some are renamed from what you get from
# following the recipe in the docker documentation.  Here, we've used
# underscores exclusively, never dashes. Some files have been renamed for
# explicitness, clarity and consistency (e.g. 'key' is renamed 'client_key').
declare CERTS_DIR=~/.docker-certs
declare CA_FILE="$CERTS_DIR"/ca_cert.pem
declare CA_KEY_FILE="$CERTS_DIR"/ca_key.pem
declare CLIENT_CERT_FILE="$CERTS_DIR"/client_cert.pem
declare CLIENT_KEY_FILE="$CERTS_DIR"/client_key.pem
declare SERVER_CERT_FILE="$CERTS_DIR"/server_cert.pem
declare SERVER_KEY_FILE="$CERTS_DIR"/server_key.pem

declare GROUP=users
declare YAHOO_GROUP="$GROUP"

function HelpAndExit {
    cat <<EOF
Usage: ${0##*/} <command>...
Setup Docker.

Commands:
  all             Setup docker home and TLS certificates/keys.
                  Same as following commands: home certs
  certs           Generate and install TLS keys.
                  Same as following commands: generate-certs install-certs
  generate-certs  Generate TLS-related certificates and keys to
                    $CERTS_DIR
  help            Print this message.
  install-certs   Install TLS-related certificates and keys in
                    $CERTS_DIR
                  to /etc/dockercert_{daemon,cli,container}.
  home            Add docker user and make symbolic links from Docker dirs in
                  /var to dirs below ~docker.
EOF

    exit 0
}

function GenerateCertificates {
    rm -rf "$CERTS_DIR"
    mkdir -p "$CERTS_DIR"

    # Generate CA private and public keys
    echo "We're about to generate a CA key, please use a secure password."
    echo "You will be prompted for this password many times in what follows..."
    openssl genrsa -aes256 -out "$CA_KEY_FILE" 4096
    openssl req -new -x509 -days "$DAYS_VALID" -key "$CA_KEY_FILE" -sha256 \
            -out "$CA_FILE"

    # Generate server key and certificate signing request (CSR)
    openssl genrsa -out "$SERVER_KEY_FILE" 4096
    local server_csr_file="$CERTS_DIR"/server.csr
    openssl req -subj "/CN=$HOSTNAME" -sha256 -new -key "$SERVER_KEY_FILE" \
            -out "$server_csr_file"

    # Sign server's public key with CA
    local server_config_file="$CERTS_DIR"/server.cnf
    echo "subjectAltName = IP:127.0.0.1" > "$server_config_file"
    openssl x509 -req -days "$DAYS_VALID" -sha256 -in "$server_csr_file" \
            -CA "$CA_FILE" -CAkey "$CA_KEY_FILE" -CAcreateserial \
            -out "$SERVER_CERT_FILE" -extfile "$server_config_file"

    # Generate client key and certificate signing request (CSR)
    openssl genrsa -out "$CLIENT_KEY_FILE" 4096
    local client_csr_file="$CERTS_DIR"/client.csr
    openssl req -subj '/CN=client' -new -key "$CLIENT_KEY_FILE" \
            -out "$client_csr_file"

    # Sign client's public key with CA
    local client_config_file="$CERTS_DIR"/client.cnf
    echo extendedKeyUsage = clientAuth > "$client_config_file"
    openssl x509 -req -days "$DAYS_VALID" -sha256 -in "$client_csr_file" \
            -CA "$CA_FILE" -CAkey "$CA_KEY_FILE" -CAcreateserial \
            -out "$CLIENT_CERT_FILE" -extfile "$client_config_file"

    # CSR and config files no longer needed
    rm "$client_csr_file" "$server_csr_file"
    rm "$server_config_file" "$client_config_file"

    # Avoid accidental writes
    chmod 0400 "$CA_KEY_FILE" "$CLIENT_KEY_FILE" "$SERVER_KEY_FILE"
    chmod 0444 "$CA_FILE" "$SERVER_CERT_FILE" "$CLIENT_CERT_FILE"
}

function InstallCertificates {
    # The files you end up with after GenerateKeys will be used by three
    # parties: The docker daemon, the docker CLI, and the docker client in Node
    # Admin. None of these parties need (nor should they have) access to all
    # these files. Also, the three parties will run as different users. Since
    # these files should not be world-readable, one solution is to create
    # separate directories for the three usages, so each directory may contain
    # only the needed files, with the correct owner and permissions.

    sudo mkdir -p /etc/dockercert_daemon
    sudo chown yahoo:users /etc/dockercert_daemon
    sudo cp "$CA_FILE" "$SERVER_CERT_FILE" "$SERVER_KEY_FILE" /etc/dockercert_daemon
    sudo chown root:root /etc/dockercert_daemon/*

    # The docker client looks for files with certain names (you can only
    # configure the path to the directory containing the files), so the
    # "original" file names are used.
    sudo mkdir -p /etc/dockercert_cli
    sudo chown yahoo:users /etc/dockercert_cli
    sudo cp "$CA_FILE" /etc/dockercert_cli/ca.pem
    sudo cp "$CLIENT_CERT_FILE" /etc/dockercert_cli/cert.pem
    sudo cp "$CLIENT_KEY_FILE" /etc/dockercert_cli/key.pem
    sudo chown $USER:$GROUP /etc/dockercert_cli/*

    sudo mkdir -p /etc/dockercert_container
    sudo chown yahoo:$YAHOO_GROUP /etc/dockercert_container
    # These filenames must match the config given in
    # src/main/application/services.xml.
    sudo cp "$CA_FILE" "$CLIENT_CERT_FILE" "$CLIENT_KEY_FILE" /etc/dockercert_container
    sudo chown yahoo:$YAHOO_GROUP /etc/dockercert_container/*

    echo "Note: Consider reloading & restarting the docker daemon to pick up"
    echo "the new certificates and keys:"
    echo "  sudo systemctl daemon-reload"
    echo "  sudo systemctl restart docker"
}

function SetupDockerHome {
    # Assume an error means the docker user already exists
    sudo useradd -g docker docker || true

    sudo mkdir -p ~docker/lib ~docker/run
    sudo chmod +rx ~docker ~docker/lib ~docker/run
    sudo systemctl stop docker
    sudo rm -rf /var/{run,lib}/docker
    sudo ln -s ~docker/run /var/run/docker
    sudo ln -s ~docker/lib /var/lib/docker
    sudo systemctl daemon-reload
    sudo systemctl restart docker
}

function Main {
    # Prime sudo
    sudo true

    if (($# == 0))
    then
        HelpAndExit
    fi

    local command
    for command in "$@"
    do
        case "$command" in
            all) Main home certs ;;
            certs)
                GenerateCertificates
                InstallCertificates
                ;;
            generate-certs) GenerateCertificates ;;
            help) HelpAndExit ;;
            home) SetupDockerHome ;;
            install-certs) InstallCertificates ;;
            *) Fail "Unknown command '$command'" ;;
        esac
    done
}

Main "$@"
