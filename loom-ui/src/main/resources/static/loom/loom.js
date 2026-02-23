(function() {
    let graphs = [];

    async function fetchGraphs() {
        try {
            const response = await fetch('/loom/api/graphs');
            graphs = await response.json();
            renderApiList();
            if (graphs.length > 0) {
                selectApi(0);
            }
        } catch (error) {
            console.error('Failed to fetch graphs:', error);
        }
    }

    function renderApiList() {
        const list = document.getElementById('api-list');
        list.innerHTML = '';

        graphs.forEach((api, index) => {
            const card = document.createElement('div');
            card.className = 'api-card';
            card.dataset.index = index;
            card.innerHTML = `
                <span class="method method-${api.method}">${api.method}</span>
                <span class="path">${api.path}</span>
                <div><span class="type-badge">${api.type}</span></div>
            `;
            card.addEventListener('click', () => selectApi(index));
            list.appendChild(card);
        });
    }

    function selectApi(index) {
        document.querySelectorAll('.api-card').forEach(c => c.classList.remove('active'));
        const card = document.querySelector(`.api-card[data-index="${index}"]`);
        if (card) card.classList.add('active');

        const api = graphs[index];
        document.getElementById('current-api').textContent =
            `${api.method} ${api.path}` + (api.responseType ? ` → ${api.responseType}` : '');

        renderDag(api);
    }

    function renderDag(api) {
        const svg = d3.select('#dag-svg');
        svg.selectAll('*').remove();
        const inner = svg.append('g');

        const g = new dagreD3.graphlib.Graph().setGraph({
            rankdir: 'LR',
            marginx: 40,
            marginy: 40,
            ranksep: 80,
            nodesep: 40
        });

        api.nodes.forEach(node => {
            let cssClass = 'node-required';
            if (node.terminal) cssClass = 'node-terminal';
            else if (!node.required) cssClass = 'node-optional';

            let label = node.name;
            if (node.outputType) label += `\n→ ${node.outputType}`;
            if (!node.required) label += '\n(optional)';
            if (node.terminal) label += '\n★ terminal';

            g.setNode(node.name, {
                label: label,
                class: cssClass,
                shape: 'rect',
                padding: 12,
                width: 180,
                height: 60
            });
        });

        api.edges.forEach(edge => {
            g.setEdge(edge.from, edge.to, {
                arrowhead: 'vee',
                curve: d3.curveBasis
            });
        });

        const render = new dagreD3.render();
        render(inner, g);

        // Zoom and pan
        const zoom = d3.zoom().on('zoom', (event) => {
            inner.attr('transform', event.transform);
        });
        svg.call(zoom);

        // Auto-fit
        const bounds = inner.node().getBBox();
        const parent = svg.node().parentElement;
        const fullWidth = parent.clientWidth;
        const fullHeight = parent.clientHeight;
        const scale = Math.min(
            fullWidth / (bounds.width + 80),
            fullHeight / (bounds.height + 80),
            1.5
        );
        const translateX = (fullWidth - bounds.width * scale) / 2;
        const translateY = (fullHeight - bounds.height * scale) / 2;

        svg.call(zoom.transform, d3.zoomIdentity
            .translate(translateX, translateY)
            .scale(scale));
    }

    fetchGraphs();
})();
