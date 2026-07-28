# Orbis Trade AI

Aplicativo Android experimental para leitura de gráficos exibidos na tela, validação estatística de estratégias e operação exclusivamente em ambiente de demonstração durante a fase de pesquisa.

## Estado atual

Sprint 1 — infraestrutura Android.

Implementado:

- Kotlin e Jetpack Compose;
- navegação entre controle e diagnóstico;
- estrutura MVVM;
- serviço de overlay flutuante;
- captura de tela com MediaProjection;
- contador de frames para validação operacional;
- CI para build e testes.

## Como executar

1. Abra o projeto no Android Studio com JDK 17.
2. Execute o app em Android 8.0 ou superior.
3. Conceda a permissão de sobreposição.
4. Inicie o overlay.
5. Autorize a captura de tela.
6. Abra a aba Diagnóstico e confirme que o contador de frames está aumentando.

## Limites da versão atual

O Sprint 1 não detecta candles, não calcula indicadores, não emite sinais e não executa operações. Essas entregas pertencem aos Sprints posteriores definidos em `ROADMAP.md`.

## Aviso

Projeto de estudo e validação em conta demo. Não há promessa de resultado financeiro.
