// Tema "Central" para Pegasus Frontend (alpha16, Qt 5.15)
// Pensado para multimidia de carro: 1024x600, GPU Mali-450 (GLES 2.0),
// controle de videogame. Sem shaders, sem desfoque, animacoes minimas.

import QtQuick 2.7
import SortFilterProxyModel 0.2

FocusScope {
    id: root

    // ---------- escala: o layout foi desenhado para 1024x600 ----------
    readonly property real escala: Math.min(width / 1024, height / 600)
    function px(v) { return Math.round(v * escala); }

    // ---------- fontes padrao do Pegasus (com reserva para versoes antigas) ----------
    readonly property string fonteSans: {
        try { return global.fonts.sans; } catch (e1) {}
        try { return globalFonts.sans; } catch (e2) {}
        return "sans-serif";
    }
    readonly property string fonteCondensada: {
        try { return global.fonts.condensed; } catch (e1) {}
        try { return globalFonts.condensed; } catch (e2) {}
        return fonteSans;
    }

    // ---------- cores ----------
    readonly property color corPainel: "#0e1218"
    readonly property color corDestaque: "#ffb300"
    readonly property color corTexto: "#ffffff"
    readonly property color corTextoFraco: "#a9b3c1"

    // ---------- sistemas (esconde "android") ----------
    SortFilterProxyModel {
        id: sistemas
        sourceModel: api.collections
        filters: [
            ValueFilter { roleName: "shortName"; value: "android"; inverted: true },
            ValueFilter { roleName: "shortName"; value: "baixar"; inverted: true }
        ]
        // Coleções por tema (Lutas, Mario...) primeiro; depois os sistemas, em ordem alfabética.
        sorters: ExpressionSorter {
            expression: {
                var a = modelLeft.shortName.indexOf("tema-") === 0, b = modelRight.shortName.indexOf("tema-") === 0;
                if (a !== b) return a;
                return modelLeft.name.localeCompare(modelRight.name) < 0;
            }
        }
    }

    // "Baixar jogos novos": coleção escondida da lista; o botão de baixo abre o baixador do Lançador
    function baixarJogos() {
        for (var i = 0; i < api.collections.count; i++) {
            var c = api.collections.get(i);
            if (c.shortName === "baixar" && c.games.count > 0) {
                api.memory.set("foco", "lista");
                c.games.get(0).launch();
                return;
            }
        }
    }

    readonly property var colecaoAtual: {
        if (sistemas.count === 0 || lista.currentIndex < 0)
            return null;
        return api.collections.get(sistemas.mapToSource(lista.currentIndex));
    }
    readonly property var jogoAtual: {
        if (!colecaoAtual || grade.count === 0 || grade.currentIndex < 0)
            return null;
        return colecaoAtual.games.get(grade.currentIndex);
    }

    property bool memoriaCarregada: false

    // ---------- acoes ----------
    function entrarNaGrade() {
        if (grade.count > 0) {
            if (grade.currentIndex < 0)
                grade.currentIndex = 0;
            grade.focus = true;
        }
    }

    function voltarParaLista() {
        lista.focus = true;
    }

    // Controle de qualquer marca: todo botão que não seja seta, voltar, troca de página,
    // volume ou mídia (botões do volante) abre o jogo. Gravado pelo app Configurar Controle.
    function botaoDeAbrir(event) {
        var k = event.key;
        if (k === Qt.Key_Up || k === Qt.Key_Down || k === Qt.Key_Left || k === Qt.Key_Right)
            return false;
        if (api.keys.isCancel(event) || api.keys.isPrevPage(event) || api.keys.isNextPage(event))
            return false;
        if (k >= 0x01000020 && k <= 0x01000026)   // shift, ctrl, alt...
            return false;
        if (k >= 0x01000061 && k <= 0x010000ff)   // voltar, volume, mídia, menu
            return false;
        return true;
    }

    function jogar() {
        if (!colecaoAtual || !jogoAtual)
            return;
        api.memory.set("sistema", colecaoAtual.shortName);
        api.memory.set("jogo", jogoAtual.title);
        api.memory.set("foco", "grade");
        jogoAtual.launch();
    }

    function trocarSistema(delta) {
        if (sistemas.count === 0)
            return;
        var n = lista.currentIndex + delta;
        if (n < 0) n = sistemas.count - 1;
        if (n >= sistemas.count) n = 0;
        lista.currentIndex = n;
    }

    // L1/R1 trocam de sistema de qualquer lugar
    Keys.onPressed: {
        if (api.keys.isPrevPage(event)) {
            event.accepted = true;
            trocarSistema(-1);
        } else if (api.keys.isNextPage(event)) {
            event.accepted = true;
            trocarSistema(1);
        }
    }

    // ---------- fundo: imagem do sistema escurecida ----------
    Rectangle {
        anchors.fill: parent
        color: "#000000"
    }

    Image {
        id: fundo
        anchors.fill: parent
        source: colecaoAtual ? "assets/sistemas/" + colecaoAtual.shortName + ".jpg" : ""
        fillMode: Image.PreserveAspectCrop
        asynchronous: true
        cache: true
        smooth: false
        sourceSize.width: 1024
        visible: status === Image.Ready
    }

    // reserva: gradiente com o nome quando nao ha imagem do sistema
    Rectangle {
        anchors.fill: parent
        visible: fundo.status === Image.Error || fundo.source == ""
        gradient: Gradient {
            GradientStop { position: 0.0; color: "#1d2b45" }
            GradientStop { position: 1.0; color: "#05070b" }
        }

        Text {
            anchors.right: parent.right
            anchors.bottom: parent.bottom
            anchors.rightMargin: px(24)
            anchors.bottomMargin: px(40)
            text: colecaoAtual ? colecaoAtual.name : ""
            color: "#ffffff"
            opacity: 0.12
            font.family: fonteCondensada
            font.pixelSize: px(90)
            font.bold: true
        }
    }

    // escurecimento para o texto ficar legivel
    Rectangle {
        anchors.fill: parent
        color: "#000000"
        opacity: 0.70
    }

    // ---------- coluna da esquerda: sistemas ----------
    Rectangle {
        id: colunaEsquerda
        anchors.top: parent.top
        anchors.bottom: parent.bottom
        anchors.left: parent.left
        width: Math.round(parent.width * 0.30)
        color: corPainel
        opacity: 0.94
    }

    Text {
        id: tituloEsquerda
        anchors.top: parent.top
        anchors.left: colunaEsquerda.left
        anchors.topMargin: px(22)
        anchors.leftMargin: px(22)
        text: "JOGOS"
        color: corDestaque
        font.family: fonteCondensada
        font.pixelSize: px(26)
        font.bold: true
        font.letterSpacing: px(3)
    }

    ListView {
        id: lista
        anchors.top: tituloEsquerda.bottom
        anchors.topMargin: px(16)
        anchors.bottom: botaoBaixar.top
        anchors.bottomMargin: px(8)
        anchors.left: colunaEsquerda.left
        anchors.right: colunaEsquerda.right
        clip: true
        focus: true

        model: sistemas
        keyNavigationWraps: true
        highlightMoveDuration: 120
        highlightRangeMode: ListView.ApplyRange
        preferredHighlightBegin: px(60)
        preferredHighlightEnd: height - px(120)

        highlight: Rectangle {
            width: lista.width
            height: px(64)
            color: lista.activeFocus ? root.corDestaque : "#3a4352"
        }

        delegate: Item {
            id: itemSistema
            width: ListView.view.width
            height: px(64)

            readonly property bool atual: ListView.isCurrentItem
            readonly property bool escuro: atual && lista.activeFocus

            Rectangle {
                anchors.left: parent.left
                anchors.top: parent.top
                anchors.bottom: parent.bottom
                width: px(6)
                color: root.corDestaque
                visible: itemSistema.atual && !lista.activeFocus
            }

            Text {
                anchors.left: parent.left
                anchors.right: parent.right
                anchors.leftMargin: px(22)
                anchors.rightMargin: px(12)
                anchors.verticalCenter: parent.verticalCenter
                text: modelData.name
                color: itemSistema.escuro ? "#111111" : (itemSistema.atual ? root.corTexto : root.corTextoFraco)
                font.family: root.fonteSans
                font.pixelSize: px(22)
                font.bold: itemSistema.atual
                elide: Text.ElideRight
            }

            MouseArea {
                anchors.fill: parent
                onClicked: {
                    if (lista.currentIndex === index && lista.activeFocus) {
                        root.entrarNaGrade();
                    } else {
                        lista.currentIndex = index;
                        lista.focus = true;
                    }
                }
            }
        }

        Keys.onPressed: {
            if (event.isAutoRepeat && api.keys.isAccept(event))
                return;
            if (api.keys.isAccept(event) || event.key === Qt.Key_Right) {
                event.accepted = true;
                root.entrarNaGrade();
            } else if (event.key === Qt.Key_Down && currentIndex === count - 1) {
                event.accepted = true;
                botaoBaixar.focus = true;
            }
        }

        onCurrentIndexChanged: {
            if (root.memoriaCarregada && root.colecaoAtual)
                api.memory.set("sistema", root.colecaoAtual.shortName);
        }
    }

    // ---------- botão fixo: baixar jogos novos do servidor ----------
    Rectangle {
        id: botaoBaixar
        anchors.left: colunaEsquerda.left
        anchors.right: colunaEsquerda.right
        anchors.bottom: ajuda.top
        anchors.margins: px(12)
        height: px(56)
        radius: px(10)
        color: activeFocus ? root.corDestaque : "#1d2735"
        border.width: activeFocus ? 0 : px(1)
        border.color: "#3a4352"

        Text {
            anchors.centerIn: parent
            text: "⬇  Baixar jogos novos"
            color: botaoBaixar.activeFocus ? "#111111" : root.corDestaque
            font.family: root.fonteSans
            font.pixelSize: px(19)
            font.bold: true
        }

        MouseArea {
            anchors.fill: parent
            onClicked: root.baixarJogos()
        }

        Keys.onPressed: {
            if (event.isAutoRepeat)
                return;
            if (api.keys.isAccept(event)) {
                event.accepted = true;
                root.baixarJogos();
            } else if (event.key === Qt.Key_Up || api.keys.isCancel(event)) {
                event.accepted = true;
                lista.focus = true;
            } else if (event.key === Qt.Key_Right) {
                event.accepted = true;
                root.entrarNaGrade();
            }
        }
    }

    // ---------- area da direita ----------
    Item {
        id: areaDireita
        anchors.top: parent.top
        anchors.bottom: ajuda.top
        anchors.left: colunaEsquerda.right
        anchors.right: parent.right
        anchors.leftMargin: px(18)
        anchors.rightMargin: px(14)

        Text {
            id: nomeSistema
            anchors.top: parent.top
            anchors.topMargin: px(16)
            anchors.left: parent.left
            anchors.right: contagem.left
            anchors.rightMargin: px(8)
            text: root.colecaoAtual ? root.colecaoAtual.name : "Nenhum sistema"
            color: root.corTextoFraco
            font.family: root.fonteCondensada
            font.pixelSize: px(18)
            elide: Text.ElideRight
        }

        Text {
            id: contagem
            anchors.baseline: nomeSistema.baseline
            anchors.right: parent.right
            text: grade.count === 1 ? "1 jogo" : grade.count + " jogos"
            color: root.corTextoFraco
            font.family: root.fonteCondensada
            font.pixelSize: px(16)
        }

        Text {
            id: tituloJogo
            anchors.top: nomeSistema.bottom
            anchors.topMargin: px(2)
            anchors.left: parent.left
            anchors.right: parent.right
            text: root.jogoAtual ? root.jogoAtual.title
                                 : (root.colecaoAtual ? "Nenhum jogo neste sistema" : "")
            color: grade.activeFocus ? root.corDestaque : root.corTexto
            font.family: root.fonteSans
            font.pixelSize: px(28)
            font.bold: true
            elide: Text.ElideRight
        }

        GridView {
            id: grade
            anchors.top: tituloJogo.bottom
            anchors.topMargin: px(10)
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.bottom: parent.bottom
            clip: true

            readonly property int colunas: 4
            cellWidth: Math.floor(width / colunas)
            cellHeight: Math.round(cellWidth * 1.22)

            model: root.colecaoAtual ? root.colecaoAtual.games : null
            keyNavigationWraps: false
            highlightMoveDuration: 120
            highlightRangeMode: GridView.ApplyRange
            preferredHighlightBegin: px(10)
            preferredHighlightEnd: height - px(10)
            cacheBuffer: cellHeight * 2

            delegate: Item {
                id: itemJogo
                width: GridView.view.cellWidth
                height: GridView.view.cellHeight

                readonly property bool atual: GridView.isCurrentItem
                readonly property bool comFoco: atual && grade.activeFocus

                // moldura de selecao
                Rectangle {
                    id: moldura
                    anchors.fill: parent
                    anchors.margins: px(4)
                    color: itemJogo.comFoco ? "#33ffb300" : (itemJogo.atual ? "#22ffffff" : "transparent")
                    border.width: itemJogo.atual ? px(3) : 0
                    border.color: itemJogo.comFoco ? root.corDestaque : "#80ffffff"
                    radius: px(6)
                }

                Item {
                    id: areaCapa
                    anchors.top: parent.top
                    anchors.left: parent.left
                    anchors.right: parent.right
                    anchors.margins: px(12)
                    height: parent.height - px(60)

                    Image {
                        id: capa
                        anchors.fill: parent
                        source: modelData.assets.boxFront
                        fillMode: Image.PreserveAspectFit
                        asynchronous: true
                        cache: true
                        smooth: true
                        sourceSize.width: 256
                        sourceSize.height: 256
                        visible: status === Image.Ready
                    }

                    // reserva quando nao ha capa
                    Rectangle {
                        anchors.fill: parent
                        visible: capa.status !== Image.Ready
                        color: "#263040"
                        radius: px(4)

                        Text {
                            anchors.fill: parent
                            anchors.margins: px(8)
                            text: capa.status === Image.Loading ? "" : modelData.title
                            color: root.corTextoFraco
                            font.family: root.fonteCondensada
                            font.pixelSize: px(16)
                            wrapMode: Text.WordWrap
                            horizontalAlignment: Text.AlignHCenter
                            verticalAlignment: Text.AlignVCenter
                            elide: Text.ElideRight
                        }
                    }
                }

                Text {
                    anchors.top: areaCapa.bottom
                    anchors.topMargin: px(6)
                    anchors.left: parent.left
                    anchors.right: parent.right
                    anchors.leftMargin: px(10)
                    anchors.rightMargin: px(10)
                    height: px(40)
                    text: modelData.title
                    color: itemJogo.comFoco ? root.corTexto : root.corTextoFraco
                    font.family: root.fonteCondensada
                    font.pixelSize: px(15)
                    font.bold: itemJogo.atual
                    wrapMode: Text.WordWrap
                    maximumLineCount: 2
                    elide: Text.ElideRight
                    horizontalAlignment: Text.AlignHCenter
                }

                MouseArea {
                    anchors.fill: parent
                    onClicked: {
                        if (grade.currentIndex === index && grade.activeFocus) {
                            root.jogar();
                        } else {
                            grade.currentIndex = index;
                            grade.focus = true;
                        }
                    }
                }
            }

            Keys.onPressed: {
                if (api.keys.isAccept(event) || root.botaoDeAbrir(event)) {
                    event.accepted = true;
                    if (!event.isAutoRepeat)
                        root.jogar();
                    return;
                }
                if (api.keys.isCancel(event)) {
                    event.accepted = true;
                    if (!event.isAutoRepeat)
                        root.voltarParaLista();
                    return;
                }
                if (event.key === Qt.Key_Left && currentIndex % colunas === 0) {
                    event.accepted = true;
                    root.voltarParaLista();
                    return;
                }
                // descer na ultima linha incompleta vai para o ultimo jogo
                if (event.key === Qt.Key_Down
                        && currentIndex + colunas >= count
                        && Math.floor(currentIndex / colunas) < Math.floor((count - 1) / colunas)) {
                    event.accepted = true;
                    currentIndex = count - 1;
                    return;
                }
            }

            onCountChanged: {
                if (count === 0 && activeFocus)
                    root.voltarParaLista();
            }
        }
    }

    // ---------- linha de ajuda ----------
    Rectangle {
        id: ajuda
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.bottom: parent.bottom
        height: px(30)
        color: "#000000"
        opacity: 0.85
    }

    Text {
        anchors.verticalCenter: ajuda.verticalCenter
        anchors.right: ajuda.right
        anchors.rightMargin: px(16)
        text: "A Jogar  ·  B Voltar  ·  Start+Select sai do jogo"
        color: root.corTextoFraco
        font.family: root.fonteSans
        font.pixelSize: px(14)
    }

    Text {
        anchors.verticalCenter: ajuda.verticalCenter
        anchors.left: ajuda.left
        anchors.leftMargin: px(22)
        text: "L/R troca de sistema"
        color: "#6f7a89"
        font.family: root.fonteSans
        font.pixelSize: px(13)
    }

    // ---------- memoria: reabrir no mesmo sistema/jogo ----------
    // adiado com Qt.callLater para o filtro de sistemas ja estar pronto
    Component.onCompleted: Qt.callLater(restaurarMemoria)

    function restaurarMemoria() {
        var sis = api.memory.get("sistema");
        if (sis) {
            for (var i = 0; i < sistemas.count; i++) {
                if (api.collections.get(sistemas.mapToSource(i)).shortName === sis) {
                    lista.currentIndex = i;
                    break;
                }
            }
        }
        lista.positionViewAtIndex(lista.currentIndex, ListView.Contain);

        var titulo = api.memory.get("jogo");
        if (titulo && colecaoAtual) {
            var jogos = colecaoAtual.games.toVarArray();
            for (var j = 0; j < jogos.length; j++) {
                if (jogos[j].title === titulo) {
                    grade.currentIndex = j;
                    grade.positionViewAtIndex(j, GridView.Contain);
                    break;
                }
            }
        }

        if (api.memory.get("foco") === "grade" && grade.count > 0)
            grade.focus = true;
        else
            lista.focus = true;
        api.memory.set("foco", "lista");

        memoriaCarregada = true;
    }
}
