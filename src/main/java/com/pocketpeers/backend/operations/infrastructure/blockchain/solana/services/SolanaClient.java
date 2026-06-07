package com.pocketpeers.backend.operations.infrastructure.blockchain.solana.services;

import lombok.Getter;
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.utils.Base58;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Getter
public class SolanaClient {
    private final RpcClient rpcClient;
    private final Account signerAccount;

    public SolanaClient(
            @Value("${solana.rpc.url}") String rpcUrl,
            @Value("${solana.credentials.privateKey}") String base58PrivateKey
    ) {
        this.rpcClient = new RpcClient(rpcUrl);

        byte[] secretKey = Base58.decode(base58PrivateKey);
        this.signerAccount = new Account(secretKey);
    }
}
