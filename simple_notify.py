from flask import Flask
from flask import request
import sys

app = Flask(__name__)

@app.route('/notify', methods=['NOTIFY'])
def log():
    sys.stdout.write('Body: {}\r\n'.format(request.headers))
    sys.stdout.flush()
    sys.stdout.write('Body: {}\r\n'.format(request.get_data()))
    sys.stdout.flush()
    return '200: OK'

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8080)