import { Controller, Get, Param } from '@nestjs/common';
import { ClientsService } from './clients.service';
import type { Client } from './client.interface';

@Controller('clients')
export class ClientsController {
  constructor(private readonly clientsService: ClientsService) {}

  @Get(':clientId')
  findById(@Param('clientId') clientId: string): Client {
    return this.clientsService.findById(clientId);
  }
}
